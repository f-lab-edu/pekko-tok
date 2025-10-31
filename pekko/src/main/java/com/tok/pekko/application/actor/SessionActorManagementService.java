package com.tok.pekko.application.actor;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.model.ChatChannelEntity;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.global.actor.GuardianActor;
import com.tok.pekko.global.actor.GuardianActor.GuardianCommand;
import com.tok.pekko.global.actor.GuardianActor.SpawnChatChannelReader;
import com.tok.pekko.global.actor.GuardianActor.SpawnClientSession;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionActorManagementService {

    private final ClusterSharding clusterSharding;
    private final ChannelMembershipPort channelMembershipPort;
    private final ActorSystem<GuardianCommand> actorSystem;
    private final Map<NodeReaderKey, ActorRef<ChatChannelReaderCommand>> localChatChannelReaders;

    public void registerSession(ClientMessageSender clientMessageSender, Long userId, Long channelId) {
        EntityRef<ChatChannelEntityCommand> chatChannel = findChatChannelEntityRef(channelId);

        spawnClientSession(clientMessageSender, userId)
                .thenCompose(clientSession -> spawnChatChannelReader(chatChannel, clientSession, channelId))
                .thenAccept(
                        chatChannelReader -> {
                            localChatChannelReaders.put(new NodeReaderKey(channelId, userId), chatChannelReader);

                            chatChannel.tell(new RegisterReader(userId, chatChannelReader));
                        }
                );
    }

    public void terminateSession(Long channelId, Long userId) {
        NodeReaderKey key = new NodeReaderKey(channelId, userId);
        ActorRef<ChatChannelReaderCommand> removedChatChannelReader = localChatChannelReaders.remove(key);

        if (removedChatChannelReader != null) {
            removeReaderSession(channelId, userId, removedChatChannelReader);
        }
    }

    private CompletionStage<ActorRef<ClientSessionCommand>> spawnClientSession(
            ClientMessageSender clientMessageSender,
            Long userId
    ) {
        return AskPattern.ask(
                actorSystem,
                (ActorRef<GuardianCommand> replyTo) -> new SpawnClientSession(
                        userId,
                        clientMessageSender,
                        channelMembershipPort,
                        replyTo
                ),
                Duration.ofSeconds(3),
                actorSystem.scheduler()
        ).thenApply(response -> {
            GuardianActor.SpawnedClientSession spawned = (GuardianActor.SpawnedClientSession) response;

            return spawned.clientSession();
        });
    }

    private CompletionStage<ActorRef<ChatChannelReaderCommand>> spawnChatChannelReader(
            EntityRef<ChatChannelEntityCommand> chatChannel,
            ActorRef<ClientSessionCommand> clientSession,
            Long channelId
    ) {
        return AskPattern.ask(
                actorSystem,
                (ActorRef<GuardianCommand> replyTo) -> new SpawnChatChannelReader(
                        channelId,
                        chatChannel,
                        clientSession,
                        replyTo
                ),
                Duration.ofSeconds(3),
                actorSystem.scheduler()
        ).thenApply(response -> {
            GuardianActor.SpawnedChatChannelReader spawned = (GuardianActor.SpawnedChatChannelReader) response;

            return spawned.channelReader();
        });
    }

    private void removeReaderSession(
            Long channelId,
            Long userId,
            ActorRef<ChatChannelReaderCommand> removeChatChannelReader
    ) {
        removeChatChannelReader.tell(new ChatChannelReaderProtocol.Shutdown());

        EntityRef<ChatChannelEntityCommand> chatChannelEntityRef = findChatChannelEntityRef(channelId);

        chatChannelEntityRef.tell(new RemoveShutdownReader(userId));
    }

    private EntityRef<ChatChannelEntityCommand> findChatChannelEntityRef(Long channelId) {
        return clusterSharding.entityRefFor(
                ChatChannelEntity.ENTITY_TYPE_KEY, String.valueOf(channelId)
        );
    }
}
