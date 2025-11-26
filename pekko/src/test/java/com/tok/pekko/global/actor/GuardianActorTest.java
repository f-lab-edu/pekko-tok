package com.tok.pekko.global.actor;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorMessagePort;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.global.actor.GuardianActor.GuardianCommand;
import com.tok.pekko.global.actor.GuardianActor.SpawnClientSession;
import com.tok.pekko.global.actor.GuardianActor.SpawnedClientSession;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Clock;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Join;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GuardianActorTest {

    private static Config config;
    private static ActorTestKit testKit;
    private ActorRef<GuardianCommand> guardianActor;
    private TestProbe<GuardianCommand> responseProbe;

    @BeforeAll
    static void setupClass() {
        config = ConfigFactory.load();
        testKit = ActorTestKit.create(config);
    }

    @BeforeEach
    void beforeEach() {
        ActorSystem<GuardianCommand> system = ActorSystem.create(
                GuardianActor.create(
                        Clock.systemDefaultZone(),
                        mock(MessageStoragePort.class),
                        mock(ChannelActorStoragePort.class),
                        mock(ChannelMembershipActorStoragePort.class)
                ),
                "test-system",
                config
        );

        Cluster cluster = Cluster.get(system);
        cluster.manager().tell(new Join(cluster.selfMember().address()));

        guardianActor = testKit.spawn(
                GuardianActor.create(
                        Clock.systemDefaultZone(),
                        mock(MessageStoragePort.class),
                        mock(ChannelActorStoragePort.class),
                        mock(ChannelMembershipActorStoragePort.class)
                )
        );
        responseProbe = testKit.createTestProbe();
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void SpawnClientSession_메시지를_받으면_ClientSessionActor를_spawn하고_SpawnedClientSession_메시지에_ActorRef를_전달한다() {
        // given
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipActorMessagePort mockChannelMembershipActorMessagePort = mock(
                ChannelMembershipActorMessagePort.class);

        // when
        guardianActor.tell(new SpawnClientSession(
                1L,
                mockClientMessageSender,
                mockMessageStoragePort,
                mockChannelMembershipActorMessagePort,
                responseProbe.ref()
        ));

        // then
        SpawnedClientSession response = responseProbe.expectMessageClass(SpawnedClientSession.class);

        assertThat(response.clientSession()).isNotNull();
    }
}
