package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ClientSessionActor.FoundChannelReaders;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor;
import com.tok.pekko.domain.chat.actor.ChatMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ReleaseChannelReaderActor;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ReportUnhealthyChannelReader;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.SpawnedChannelReaderActor;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.UnregisterChannelReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

public class ChannelReaderRegistryActor extends AbstractBehavior<ChannelReaderRegistryCommand> {

    public static Behavior<ChannelReaderRegistryCommand> create(
            ClusterSharding clusterSharding,
            Duration heartBeatDuration
    ) {
        return Behaviors.setup(
                context ->
                        Behaviors.withTimers(
                                timers -> {
                                    timers.startTimerWithFixedDelay(new HeartBeat(), heartBeatDuration);

                                    return new ChannelReaderRegistryActor(context, clusterSharding);
                                }
                        )
        );
    }

    private final ClusterSharding clusterSharding;
    private final Map<Long, ChannelReaderNode> readers;

    public ChannelReaderRegistryActor(
            ActorContext<ChannelReaderRegistryCommand> context,
            ClusterSharding clusterSharding
    ) {
        super(context);

        this.clusterSharding = clusterSharding;
        this.readers = new HashMap<>();
    }

    @Override
    public Receive<ChannelReaderRegistryCommand> createReceive() {
        return newReceiveBuilder().onMessage(GetChannelReaderActor.class, this::onGetChannelReaderRef)
                                  .onMessage(SpawnedChannelReaderActor.class, this::onSpawnedChannelReaderActor)
                                  .onMessage(ReleaseChannelReaderActor.class, this::onReleaseChannelReaderActor)
                                  .onMessage(ReportUnhealthyChannelReader.class, this::onReportUnhealthyChannelReader)
                                  .onMessage(HeartBeat.class, this::onHeartBeat)
                                  .build();
    }

    private Behavior<ChannelReaderRegistryCommand> onGetChannelReaderRef(GetChannelReaderActor command) {
        Map<Long, ActorRef<ChannelReaderCommand>> chatChannelReaderRefs =
                command.channelIds()
                       .stream()
                       .collect(
                               Collectors.toMap(
                                       Function.identity(),
                                       channelId -> findSingletonChannelReaderActor(channelId, command.replyTo())
                               )
                       );

        command.replyTo()
               .tell(new FoundChannelReaders(chatChannelReaderRefs));

        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onSpawnedChannelReaderActor(SpawnedChannelReaderActor command) {
        ChannelReaderNode channelReaderNode = readers.get(command.channelId());

        if (channelReaderNode == null) {
            return this;
        }

        channelReaderNode.readerName = command.readerName();

        EntityRef<ChannelEntityCommand> channelEntity = findChannelEntity(command.channelId());

        channelEntity.tell(new RegisterReader(command.readerName(), command.reader()));
        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onReleaseChannelReaderActor(ReleaseChannelReaderActor command) {
        command.channelIds()
               .stream()
               .map(readers::get)
               .filter(Objects::nonNull)
               .forEach(readerNode -> readerNode.clientSessions.remove(command.clientSession()));

        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onReportUnhealthyChannelReader(
            ReportUnhealthyChannelReader command
    ) {
        for (Long channelId : command.channelIds()) {
            ChannelReaderNode unHealthyReaderNode = readers.remove(channelId);

            if (unHealthyReaderNode != null) {
                getContext().stop(unHealthyReaderNode.reader);
                unHealthyReaderNode.clientSessions
                        .forEach(clientSession -> clientSession.tell(new UnregisterChannelReader(channelId)));
            }
        }

        return this;
    }

    private Behavior<ChannelReaderRegistryCommand> onHeartBeat(HeartBeat command) {
        if (readers.isEmpty()) {
            return this;
        }

        for (Entry<Long, ChannelReaderNode> readerEntry : readers.entrySet()) {
            Long channelId = readerEntry.getKey();
            ChannelReaderNode readerNode = readerEntry.getValue();

            if (readerNode.clientSessions.isEmpty()) {
                getContext().stop(readerNode.reader);

                EntityRef<ChannelEntityCommand> channelEntity = findChannelEntity(channelId);
                channelEntity.tell(new RemoveShutdownReader(readerNode.readerName));
            }
        }

        return this;
    }

    private ActorRef<ChannelReaderCommand> findSingletonChannelReaderActor(
            Long channelId,
            ActorRef<ClientSessionCommand> clientSession
    ) {
        ChannelReaderNode channelReaderNode = readers.get(channelId);

        if (channelReaderNode != null) {
            channelReaderNode.clientSessions.add(clientSession);
            return channelReaderNode.reader;
        }

        return spawnChatChannelReaderActor(channelId, clientSession);
    }

    private ActorRef<ChannelReaderCommand> spawnChatChannelReaderActor(
            Long channelId,
            ActorRef<ClientSessionCommand> clientSession
    ) {
        EntityRef<ChannelEntityCommand> channelEntity = findChannelEntity(channelId);
        ActorRef<ChannelReaderCommand> channelReader = getContext().spawn(
                Behaviors.supervise(
                        ChannelReaderActor.create(
                                channelId,
                                new ChatMessages(),
                                channelEntity,
                                getContext().getSelf()
                        )
                ).onFailure(SupervisorStrategy.restart()),
                "chat-channel-reader-" + System.nanoTime() + "-" + channelId
        );

        ChannelReaderNode channelReaderNode = new ChannelReaderNode(channelReader);

        channelReaderNode.clientSessions.add(clientSession);
        readers.put(channelId, channelReaderNode);

        return channelReader;
    }

    private EntityRef<ChannelEntityCommand> findChannelEntity(Long channelId) {
        return clusterSharding.entityRefFor(
                ChannelEntity.ENTITY_TYPE_KEY, String.valueOf(channelId)
        );
    }

    private static class ChannelReaderNode {

        private final ActorRef<ChannelReaderCommand> reader;
        private final List<ActorRef<ClientSessionCommand>> clientSessions = new ArrayList<>();
        private String readerName;

        private ChannelReaderNode(ActorRef<ChannelReaderCommand> reader) {
            this.reader = reader;
        }
    }

    public record GetChannelReaderActor(List<Long> channelIds, ActorRef<ClientSessionCommand> replyTo) implements ChannelReaderRegistryCommand { }
    private record HeartBeat() implements ChannelReaderRegistryCommand { }
}
