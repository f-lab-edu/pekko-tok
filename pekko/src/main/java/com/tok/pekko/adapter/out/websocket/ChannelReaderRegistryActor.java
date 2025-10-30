package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ClientSessionActor.FoundChannelReaders;
import com.tok.pekko.domain.chat.model.ChatChannelEntity;
import com.tok.pekko.domain.chat.model.ChatChannelReaderActor;
import com.tok.pekko.domain.chat.model.ChatMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.PingHealthCheck;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.PongHealthCheck;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.Terminated;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

public class ChannelReaderRegistryActor extends AbstractBehavior<ChannelReaderRegistryCommand> {

    public static Behavior<ChannelReaderRegistryCommand> create(ClusterSharding clusterSharding) {
        return Behaviors.setup(
                context ->
                        Behaviors.withTimers(
                                timers -> {
                                    timers.startTimerWithFixedDelay(
                                            new HealthCheckHeartBeat(),
                                            Duration.ofSeconds(30L)
                                    );

                                    return new ChannelReaderRegistryActor(context, clusterSharding);
                                }
                        )
        );
    }

    private final ClusterSharding clusterSharding;
    private final Map<Long, ActorRef<ChatChannelReaderCommand>> readers;
    private final Map<Long, Cancellable> healthCheckTimeouts;

    public ChannelReaderRegistryActor(
            ActorContext<ChannelReaderRegistryCommand> context,
            ClusterSharding clusterSharding
    ) {
        super(context);

        this.clusterSharding = clusterSharding;
        this.readers = new HashMap<>();
        this.healthCheckTimeouts = new HashMap<>();
    }

    @Override
    public Receive<ChannelReaderRegistryCommand> createReceive() {
        return newReceiveBuilder().onMessage(GetChannelReaderActorRef.class, this::onGetChannelReaderRef)
                                  .onMessage(HealthCheckHeartBeat.class, this::onHealthCheckHeartBeat)
                                  .onMessage(PongHealthCheck.class, this::onPongHealthCheck)
                                  .onMessage(PongHealthCheckTimeout.class, this::onPongHealthCheckTimeout)
                                  .onSignal(Terminated.class, this::onTerminated)
                                  .build();
    }

    private Behavior<ChannelReaderRegistryCommand> onGetChannelReaderRef(GetChannelReaderActorRef command) {
        Map<Long, ActorRef<ChatChannelReaderCommand>> chatChannelReaderRefs =
                command.channelIds()
                       .stream()
                       .collect(
                               Collectors.toMap(
                                       Function.identity(),
                                       channelId ->
                                               findSingletonChannelReaderActorRef(
                                                       channelId,
                                                       command.userId(),
                                                       command.replyTo()
                                               )
                               )
                       );

        command.replyTo()
               .tell(new FoundChannelReaders(chatChannelReaderRefs));

        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onHealthCheckHeartBeat(HealthCheckHeartBeat command) {
        if (readers.isEmpty()) {
            return this;
        }

        for (Entry<Long, ActorRef<ChatChannelReaderCommand>> readerEntry : readers.entrySet()) {
            Long channelId = readerEntry.getKey();
            ActorRef<ChatChannelReaderCommand> readerRef = readerEntry.getValue();

            readerRef.tell(new PingHealthCheck(getContext().getSelf()));

            Cancellable timeoutSchedule = getContext().scheduleOnce(
                    Duration.ofSeconds(5L),
                    getContext().getSelf(),
                    new PongHealthCheckTimeout(channelId)
            );

            Cancellable oldSchedule = healthCheckTimeouts.put(channelId, timeoutSchedule);
            if (oldSchedule != null) {
                oldSchedule.cancel();
            }
        }

        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onPongHealthCheck(PongHealthCheck command) {
        Cancellable schedule = healthCheckTimeouts.remove(command.channelId());

        if (schedule != null) {
            schedule.cancel();
        }

        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onPongHealthCheckTimeout(PongHealthCheckTimeout command) {
        healthCheckTimeouts.remove(command.channelId());

        ActorRef<ChatChannelReaderCommand> readerRef = readers.remove(command.channelId());

        if (readerRef != null) {
            getContext().unwatch(readerRef);
            getContext().stop(readerRef);
        }

        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onTerminated(Terminated signal) {
        readers.entrySet().stream()
               .filter(entry -> entry.getValue().path().equals(signal.getRef().path()))
               .map(Map.Entry::getKey)
               .findFirst()
               .ifPresent(channelId -> {
                   readers.remove(channelId);
                   healthCheckTimeouts.remove(channelId);
               });


        return this;
    }

    private ActorRef<ChatChannelReaderCommand> findSingletonChannelReaderActorRef(
            Long channelId,
            Long userId,
            ActorRef<ClientSessionCommand> replyTo
    ) {
        ActorRef<ChatChannelReaderCommand> channelReaderActorRef = readers.get(channelId);

        if (channelReaderActorRef != null) {
            return channelReaderActorRef;
        }

        ActorRef<ChatChannelReaderCommand> channelReaderRef = spawnChatChannelReaderActor(channelId, userId, replyTo);

        readers.put(channelId, channelReaderRef);
        return channelReaderRef;
    }

    private ActorRef<ChatChannelReaderCommand> spawnChatChannelReaderActor(
            Long channelId,
            Long userId,
            ActorRef<ClientSessionCommand> replyTo
    ) {
        EntityRef<ChatChannelEntityCommand> chatChannelEntityRef = findChatChannelEntityRef(channelId);
        ActorRef<ChatChannelReaderCommand> chatChannelReaderRef = getContext().spawn(
                ChatChannelReaderActor.create(
                        channelId, new ChatMessages(), chatChannelEntityRef, replyTo
                ),
                "chat-channel-reader-" + System.nanoTime() + "-" + channelId + ":" + userId,
                Props.empty()
        );

        getContext().watch(chatChannelReaderRef);
        return chatChannelReaderRef;
    }

    private EntityRef<ChatChannelEntityCommand> findChatChannelEntityRef(Long channelId) {
        return clusterSharding.entityRefFor(
                ChatChannelEntity.ENTITY_TYPE_KEY, String.valueOf(channelId)
        );
    }

    public record GetChannelReaderActorRef(List<Long> channelIds, Long userId, ActorRef<ClientSessionCommand> replyTo) implements ChannelReaderRegistryCommand { }
    private record HealthCheckHeartBeat() implements ChannelReaderRegistryCommand { }
    private record PongHealthCheckTimeout(Long channelId) implements ChannelReaderRegistryCommand { }
}
