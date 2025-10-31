package com.tok.pekko.global.actor;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.global.actor.GuardianActor.GuardianCommand;
import com.tok.pekko.global.actor.GuardianActor.SpawnChatChannelReader;
import com.tok.pekko.global.actor.GuardianActor.SpawnClientSession;
import com.tok.pekko.global.actor.GuardianActor.SpawnedChatChannelReader;
import com.tok.pekko.global.actor.GuardianActor.SpawnedClientSession;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
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
                GuardianActor.create(),
                "test-system",
                config
        );

        Cluster cluster = Cluster.get(system);
        cluster.manager().tell(new Join(cluster.selfMember().address()));

        guardianActor = testKit.spawn(GuardianActor.create());
        responseProbe = testKit.createTestProbe();
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void SpawnClientSession_메시지를_받으면_ClientSessionActor를_spawn하고_SpawnedClientSession_메시지에_ActorRef를_전달한다() {
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);

        guardianActor.tell(new SpawnClientSession(
                1L,
                mockClientMessageSender,
                mockChannelMembershipPort,
                responseProbe.ref()
        ));

        SpawnedClientSession response = responseProbe.expectMessageClass(SpawnedClientSession.class);

        assertThat(response.clientSession()).isNotNull();
    }

    @Test
    void SpawnChatChannelReader_메시지를_받으면_ChatChannelReaderActor를_spawn하고_SpawnedChatChannelReader_메시지에_ActorRef를_전달한다() {
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe();
        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> mockEntityRef = mock(EntityRef.class);

        guardianActor.tell(new SpawnChatChannelReader(
                1L,
                mockEntityRef,
                clientSessionProbe.ref(),
                responseProbe.ref()
        ));

        SpawnedChatChannelReader response = responseProbe.expectMessageClass(SpawnedChatChannelReader.class);

        assertThat(response.channelReader()).isNotNull();
    }
}
