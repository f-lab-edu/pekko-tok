package com.tok.pekko.application.actor;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorMessagePort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncJoinChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncLeaveChannel;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.global.actor.GuardianActor;
import com.tok.pekko.global.actor.GuardianActor.GuardianCommand;
import com.tok.pekko.global.actor.GuardianActor.SpawnClientSession;
import com.tok.pekko.global.actor.GuardianActor.SpawnedClientSession;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientSessionActorManagementService {

    private final ActorSystem<GuardianCommand> actorSystem;
    private final MessageStoragePort messageStoragePort;
    private final ChannelMembershipActorMessagePort channelMembershipActorMessagePort;
    private final Map<Long, ActorRef<ClientSessionCommand>> clientSessions = new ConcurrentHashMap<>();

    public CompletionStage<ActorRef<ClientSessionCommand>> createClientSessionActor(
            ClientMessageSender clientMessageSender,
            Long userId
    ) {
        return AskPattern.ask(
                actorSystem,
                (ActorRef<GuardianCommand> replyTo) -> new SpawnClientSession(
                        userId,
                        clientMessageSender,
                        messageStoragePort,
                        channelMembershipActorMessagePort,
                        replyTo
                ),
                Duration.ofSeconds(3),
                actorSystem.scheduler()
        ).thenApply(response -> {
            SpawnedClientSession spawned = (GuardianActor.SpawnedClientSession) response;

            clientSessions.put(userId, spawned.clientSession());
            return spawned.clientSession();
        });
    }

    public ActorRef<ClientSessionCommand> findClientSession(Long userId) {
        ActorRef<ClientSessionCommand> clientSession = clientSessions.get(userId);

        if (clientSession == null) {
            throw new ClientSessionNotFoundException();
        }

        return clientSession;
    }

    public void syncJoinChannel(Long channelId, Long userId) {
        ActorRef<ClientSessionCommand> clientSession = clientSessions.get(userId);

        if (clientSession != null) {
            clientSession.tell(new SyncJoinChannel(channelId));
        }
    }

    public void syncLeaveChannel(Long channelId, Long userId) {
        ActorRef<ClientSessionCommand> clientSession = clientSessions.get(userId);

        if (clientSession != null) {
            clientSession.tell(new SyncLeaveChannel(channelId));
        }
    }

    public static class ClientSessionNotFoundException extends IllegalStateException {

        public ClientSessionNotFoundException() {
            super("지정한 ClientSessionActor를 찾을 수 없습니다.");
        }
    }
}
