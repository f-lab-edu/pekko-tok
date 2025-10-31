package com.tok.pekko.application.actor;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.global.actor.GuardianActor;
import com.tok.pekko.global.actor.GuardianActor.GuardianCommand;
import com.tok.pekko.global.actor.GuardianActor.SpawnClientSession;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateClientSessionActorService {

    private final ActorSystem<GuardianCommand> actorSystem;
    private final ChannelMembershipPort channelMembershipPort;

    public CompletionStage<ActorRef<ClientSessionCommand>> createClientSessionActor(
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
}
