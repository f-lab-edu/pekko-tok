package com.tok.pekko.application.actor;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.global.actor.GuardianActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Join;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CreateClientSessionActorServiceTest {

    static Config config;
    static ActorTestKit testKit;
    static ActorSystem<GuardianActor.GuardianCommand> actorSystem;

    @BeforeAll
    static void setup() {
        config = ConfigFactory.load();
        testKit = ActorTestKit.create(config);
        actorSystem = ActorSystem.create(
                GuardianActor.create(),
                "test-system",
                config
        );

        Cluster cluster = Cluster.get(actorSystem);
        cluster.manager().tell(new Join(cluster.selfMember().address()));
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
        actorSystem.terminate();
    }

    @Test
    void createClientSessionActor_호출시_ClientSessionActor를_생성하고_ActorRef를_반환한다() {
        // given
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        CreateClientSessionActorService service = new CreateClientSessionActorService(
                actorSystem,
                mockChannelMembershipPort
        );
        Long userId = 100L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage =
                service.createClientSessionActor(mockClientMessageSender, userId);

        // then
        assertThat(completionStage)
                .succeedsWithin(Duration.ofSeconds(5))
                .isNotNull();
    }

    @Test
    void createClientSessionActor_호출시_AskPattern으로_Guardian에_메시지를_전송한다() {
        // given
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        CreateClientSessionActorService service = new CreateClientSessionActorService(
                actorSystem,
                mockChannelMembershipPort
        );
        Long userId = 200L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage =
                service.createClientSessionActor(mockClientMessageSender, userId);

        // then
        assertThat(completionStage)
                .succeedsWithin(Duration.ofSeconds(5));
    }

    @Test
    void createClientSessionActor_반환_ActorRef가_ClientSessionCommand를_처리_가능하다() {
        // given
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        CreateClientSessionActorService service = new CreateClientSessionActorService(
                actorSystem,
                mockChannelMembershipPort
        );
        Long userId = 300L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage =
                service.createClientSessionActor(mockClientMessageSender, userId);

        // then
        ActorRef<ClientSessionCommand> clientSessionRef = completionStage
                .toCompletableFuture()
                .join();

        assertThat(clientSessionRef).isNotNull();
        assertThat(clientSessionRef.path().name()).contains("client-session");
    }

    @Test
    void createClientSessionActor_서로_다른_userId로_호출하면_다른_ActorRef를_반환한다() {
        // given
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        CreateClientSessionActorService service = new CreateClientSessionActorService(
                actorSystem,
                mockChannelMembershipPort
        );
        Long userId1 = 400L;
        Long userId2 = 500L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage1 =
                service.createClientSessionActor(mockClientMessageSender, userId1);
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage2 =
                service.createClientSessionActor(mockClientMessageSender, userId2);

        // then
        ActorRef<ClientSessionCommand> ref1 = completionStage1.toCompletableFuture().join();
        ActorRef<ClientSessionCommand> ref2 = completionStage2.toCompletableFuture().join();

        assertThat(ref1).isNotEqualTo(ref2);
    }

    @Test
    void createClientSessionActor_타임아웃_설정이_적용된다() {
        // given
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        CreateClientSessionActorService service = new CreateClientSessionActorService(
                actorSystem,
                mockChannelMembershipPort
        );
        Long userId = 600L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage =
                service.createClientSessionActor(mockClientMessageSender, userId);

        // then
        assertThat(completionStage)
                .succeedsWithin(Duration.ofSeconds(4));
    }
}
