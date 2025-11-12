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
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.SpawnedChannelReaderActor;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import java.time.Duration;
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
        return newReceiveBuilder().onMessage(GetChannelReaderActor.class, this::onGetChannelReader)
                                  .onMessage(SpawnedChannelReaderActor.class, this::onSpawnedChannelReaderActor)
                                  .onMessage(ReleaseChannelReaderActor.class, this::onReleaseChannelReaderActor)
                                  .onMessage(HeartBeat.class, this::onHeartBeat)
                                  .build();
    }

    private Behavior<ChannelReaderRegistryCommand> onGetChannelReader(GetChannelReaderActor command) {
        Map<Long, ActorRef<ChannelReaderCommand>> chatChannelReader =
                command.channelIds()
                       .stream()
                       .collect(
                               Collectors.toMap(
                                       Function.identity(),
                                       channelId -> findSingletonChannelReaderActor(
                                               command.userId(),
                                               channelId,
                                               command.replyTo()
                                       )
                               )
                       );

        command.replyTo()
               .tell(new FoundChannelReaders(chatChannelReader));

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
               .forEach(readerNode -> readerNode.clientSessions.remove(command.userId()));

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
            Long userId,
            Long channelId,
            ActorRef<ClientSessionCommand> clientSession
    ) {
        ChannelReaderNode channelReaderNode = readers.get(channelId);

        if (channelReaderNode != null) {
            channelReaderNode.clientSessions.put(userId, clientSession);
            return channelReaderNode.reader;
        }

        return spawnChatChannelReaderActor(userId, channelId, clientSession);
    }

    private ActorRef<ChannelReaderCommand> spawnChatChannelReaderActor(
            Long userId,
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

        channelReaderNode.clientSessions.put(userId, clientSession);
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
        private final Map<Long, ActorRef<ClientSessionCommand>> clientSessions = new HashMap<>();
        private String readerName;

        private ChannelReaderNode(ActorRef<ChannelReaderCommand> reader) {
            this.reader = reader;
        }
    }

    // Primary-Secondary 중 Secondary인 ChannelReaderActor의 ActorRef 조회를 요청하는 메시지
    public record GetChannelReaderActor(Long userId, List<Long> channelIds, ActorRef<ClientSessionCommand> replyTo) implements ChannelReaderRegistryCommand { }

    // 내부 타이머를 활용해 240초 간격으로 전달되는 메시지
    private record HeartBeat() implements ChannelReaderRegistryCommand { }
}
