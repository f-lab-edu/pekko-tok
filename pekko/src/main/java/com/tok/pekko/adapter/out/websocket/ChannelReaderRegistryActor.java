package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ClientSessionActor.FoundChannelReaders;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor;
import com.tok.pekko.domain.chat.actor.ChatMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.PingHealthCheckFromRegistry;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.PongHealthCheck;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.SpawnedChannelReaderActor;
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
    private final Map<Long, ChannelReaderNode> readers;
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
                                  .onMessage(SpawnedChannelReaderActor.class, this::onSpawnedChannelReaderActor)
                                  .onMessage(HealthCheckHeartBeat.class, this::onHealthCheckHeartBeat)
                                  .onMessage(PongHealthCheck.class, this::onPongHealthCheck)
                                  .onMessage(PongHealthCheckTimeout.class, this::onPongHealthCheckTimeout)
                                  .onSignal(Terminated.class, this::onTerminated)
                                  .build();
    }

    private Behavior<ChannelReaderRegistryCommand> onGetChannelReaderRef(GetChannelReaderActorRef command) {
        Map<Long, ActorRef<ChannelReaderCommand>> chatChannelReaderRefs =
                command.channelIds()
                       .stream()
                       .collect(
                               Collectors.toMap(
                                       Function.identity(),
                                       this::findSingletonChannelReaderActorRef
                               )
                       );

        command.replyTo()
               .tell(new FoundChannelReaders(chatChannelReaderRefs));

        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onSpawnedChannelReaderActor(SpawnedChannelReaderActor command) {
        ChannelReaderNode channelReaderNode = new ChannelReaderNode(command.reader(), command.readerName());

        readers.put(command.channelId(), channelReaderNode);

        EntityRef<ChannelEntityCommand> chatChannelEntityRef = findChatChannelEntityRef(command.channelId());

        chatChannelEntityRef.tell(new RegisterReader(command.readerName(), command.reader()));
        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onHealthCheckHeartBeat(HealthCheckHeartBeat command) {
        if (readers.isEmpty()) {
            return this;
        }

        for (Entry<Long, ChannelReaderNode> readerEntry : readers.entrySet()) {
            Long channelId = readerEntry.getKey();
            ActorRef<ChannelReaderCommand> readerRef = readerEntry.getValue().reader;

            readerRef.tell(new PingHealthCheckFromRegistry(getContext().getSelf()));

            Cancellable timeoutSchedule = getContext().scheduleOnce(
                    Duration.ofSeconds(60L),
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

        ChannelReaderNode channelReaderNode = readers.remove(command.channelId());

        if (channelReaderNode != null) {
            getContext().unwatch(channelReaderNode.reader);
            getContext().stop(channelReaderNode.reader);
        }

        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onTerminated(Terminated signal) {
        readers.entrySet()
               .stream()
               .filter(entry -> entry.getValue().reader.path().equals(signal.getRef().path()))
               .map(Map.Entry::getKey)
               .findFirst()
               .ifPresent(channelId -> {
                   ChannelReaderNode channelReaderNode = readers.remove(channelId);
                   healthCheckTimeouts.remove(channelId);

                   if (channelReaderNode != null) {
                       EntityRef<ChannelEntityCommand> channelEntityRef = findChatChannelEntityRef(channelId);

                       channelEntityRef.tell(new RemoveShutdownReader(channelReaderNode.readerName));
                   }
               });

        return this;
    }

    private ActorRef<ChannelReaderCommand> findSingletonChannelReaderActorRef(Long channelId) {
        ChannelReaderNode channelReaderNode = readers.get(channelId);

        if (channelReaderNode != null) {
            return channelReaderNode.reader;
        }

        return spawnChatChannelReaderActor(channelId);
    }

    private ActorRef<ChannelReaderCommand> spawnChatChannelReaderActor(Long channelId) {
        EntityRef<ChannelEntityCommand> chatChannelEntityRef = findChatChannelEntityRef(channelId);
        ActorRef<ChannelReaderCommand> chatChannelReaderRef = getContext().spawn(
                ChannelReaderActor.create(
                        channelId, new ChatMessages(), chatChannelEntityRef, getContext().getSelf()
                ),
                "chat-channel-reader-" + System.nanoTime() + "-" + channelId
        );

        getContext().watch(chatChannelReaderRef);
        return chatChannelReaderRef;
    }

    private EntityRef<ChannelEntityCommand> findChatChannelEntityRef(Long channelId) {
        return clusterSharding.entityRefFor(
                ChannelEntity.ENTITY_TYPE_KEY, String.valueOf(channelId)
        );
    }

    private static class ChannelReaderNode {

        private final ActorRef<ChannelReaderCommand> reader;
        private final String readerName;

        private ChannelReaderNode(ActorRef<ChannelReaderCommand> reader, String readerName) {
            this.reader = reader;
            this.readerName = readerName;
        }
    }

    public record GetChannelReaderActorRef(List<Long> channelIds, ActorRef<ClientSessionCommand> replyTo) implements ChannelReaderRegistryCommand { }
    private record HealthCheckHeartBeat() implements ChannelReaderRegistryCommand { }
    private record PongHealthCheckTimeout(Long channelId) implements ChannelReaderRegistryCommand { }
}
