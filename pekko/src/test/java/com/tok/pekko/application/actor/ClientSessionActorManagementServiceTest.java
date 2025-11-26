package com.tok.pekko.application.actor;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorMessagePort;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.global.actor.GuardianActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Clock;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ClientSessionActorManagementServiceTest {

    static Config config;
    static ActorTestKit testKit;
    static ActorSystem<GuardianActor.GuardianCommand> actorSystem;

    @BeforeAll
    static void setup() {
        config = ConfigFactory.load();
        testKit = ActorTestKit.create(config);
        actorSystem = ActorSystem.create(
                GuardianActor.create(
                        Clock.systemDefaultZone(),
                        mock(MessageStoragePort.class),
                        mock(ChannelActorStoragePort.class),
                        mock(ChannelMembershipActorStoragePort.class)
                ),
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
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipActorMessagePort mockChannelMembershipActorMessagePort = mock(
                ChannelMembershipActorMessagePort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ClientSessionActorManagementService service = new ClientSessionActorManagementService(
                actorSystem,
                mockMessageStoragePort,
                mockChannelMembershipActorMessagePort
        );
        Long userId = 100L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> actual =
                service.createClientSessionActor(mockClientMessageSender, userId);

        // then
        assertThat(actual).succeedsWithin(Duration.ofSeconds(5))
                          .isNotNull();
    }

    @Test
    void createClientSessionActor_호출시_AskPattern으로_Guardian에_메시지를_전송한다() {
        // given
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipActorMessagePort mockChannelMembershipActorMessagePort = mock(
                ChannelMembershipActorMessagePort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ClientSessionActorManagementService service = new ClientSessionActorManagementService(
                actorSystem,
                mockMessageStoragePort,
                mockChannelMembershipActorMessagePort
        );
        Long userId = 200L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> actual =
                service.createClientSessionActor(mockClientMessageSender, userId);

        // then
        assertThat(actual).succeedsWithin(Duration.ofSeconds(5));
    }

    @Test
    void createClientSessionActor_반환_ActorRef가_ClientSessionCommand를_처리_가능하다() {
        // given
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipActorMessagePort mockChannelMembershipActorMessagePort = mock(
                ChannelMembershipActorMessagePort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ClientSessionActorManagementService service = new ClientSessionActorManagementService(
                actorSystem,
                mockMessageStoragePort,
                mockChannelMembershipActorMessagePort
        );
        Long userId = 300L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage =
                service.createClientSessionActor(mockClientMessageSender, userId);

        // then
        ActorRef<ClientSessionCommand> actual = completionStage.toCompletableFuture()
                                                                         .join();

        assertThat(actual.path().name()).contains("client-session");
    }

    @Test
    void createClientSessionActor_서로_다른_userId로_호출하면_다른_ActorRef를_반환한다() {
        // given
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipActorMessagePort mockChannelMembershipActorMessagePort = mock(
                ChannelMembershipActorMessagePort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ClientSessionActorManagementService service = new ClientSessionActorManagementService(
                actorSystem,
                mockMessageStoragePort,
                mockChannelMembershipActorMessagePort
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
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipActorMessagePort mockChannelMembershipActorMessagePort = mock(
                ChannelMembershipActorMessagePort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ClientSessionActorManagementService service = new ClientSessionActorManagementService(
                actorSystem,
                mockMessageStoragePort,
                mockChannelMembershipActorMessagePort
        );
        Long userId = 600L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> actual =
                service.createClientSessionActor(mockClientMessageSender, userId);

        // then
        assertThat(actual).succeedsWithin(Duration.ofSeconds(4));
    }

    @Test
    void createClientSessionActor_호출_후_생성된_ActorRef가_clientSessions에_캐시된다() {
        // given
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipActorMessagePort mockChannelMembershipActorMessagePort = mock(
                ChannelMembershipActorMessagePort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ClientSessionActorManagementService service = new ClientSessionActorManagementService(
                actorSystem,
                mockMessageStoragePort,
                mockChannelMembershipActorMessagePort
        );
        Long userId = 700L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage =
                service.createClientSessionActor(mockClientMessageSender, userId);
        ActorRef<ClientSessionCommand> createdRef = completionStage.toCompletableFuture().join();

        // then
        ActorRef<ClientSessionCommand> cachedRef = service.findClientSession(userId);

        assertThat(cachedRef).isEqualTo(createdRef);
    }

    @Test
    void findClientSession_호출시_캐시된_ActorRef를_반환한다() {
        // given
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipActorMessagePort mockChannelMembershipActorMessagePort = mock(
                ChannelMembershipActorMessagePort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ClientSessionActorManagementService service = new ClientSessionActorManagementService(
                actorSystem,
                mockMessageStoragePort,
                mockChannelMembershipActorMessagePort
        );
        Long userId = 800L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage =
                service.createClientSessionActor(mockClientMessageSender, userId);
        completionStage.toCompletableFuture().join();

        ActorRef<ClientSessionCommand> actual = service.findClientSession(userId);

        // then
        assertThat(actual).isNotNull();
    }

    @Test
    void findClientSession_존재하지_않는_userId로_호출하면_ClientSessionNotFoundException을_발생시킨다() {
        // given
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipActorMessagePort mockChannelMembershipActorMessagePort = mock(
                ChannelMembershipActorMessagePort.class);
        ClientSessionActorManagementService service = new ClientSessionActorManagementService(
                actorSystem,
                mockMessageStoragePort,
                mockChannelMembershipActorMessagePort
        );
        Long nonExistentUserId = 999L;

        // when & then
        assertThatThrownBy(() -> service.findClientSession(nonExistentUserId))
                .isInstanceOf(ClientSessionActorManagementService.ClientSessionNotFoundException.class)
                .hasMessage("지정한 ClientSessionActor를 찾을 수 없습니다.");
    }

    @Test
    void createClientSessionActor_여러_userId로_호출하면_각각_캐시된다() {
        // given
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipActorMessagePort mockChannelMembershipActorMessagePort = mock(
                ChannelMembershipActorMessagePort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ClientSessionActorManagementService service = new ClientSessionActorManagementService(
                actorSystem,
                mockMessageStoragePort,
                mockChannelMembershipActorMessagePort
        );
        Long userId1 = 1000L;
        Long userId2 = 1001L;

        // when
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage1 =
                service.createClientSessionActor(mockClientMessageSender, userId1);
        CompletionStage<ActorRef<ClientSessionCommand>> completionStage2 =
                service.createClientSessionActor(mockClientMessageSender, userId2);

        ActorRef<ClientSessionCommand> ref1 = completionStage1.toCompletableFuture().join();
        ActorRef<ClientSessionCommand> ref2 = completionStage2.toCompletableFuture().join();

        // then
        ActorRef<ClientSessionCommand> cachedRef1 = service.findClientSession(userId1);
        ActorRef<ClientSessionCommand> cachedRef2 = service.findClientSession(userId2);

        assertThat(cachedRef1).isEqualTo(ref1);
        assertThat(cachedRef2).isEqualTo(ref2);
    }
}
