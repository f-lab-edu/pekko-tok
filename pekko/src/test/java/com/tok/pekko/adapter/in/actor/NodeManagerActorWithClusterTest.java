package com.tok.pekko.adapter.in.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.application.actor.NodeManagerActor;
import com.tok.pekko.domain.chat.model.ChatChannelEntity;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RequestJoin;
import com.tok.pekko.domain.chat.port.out.NodeManagerProtocol.NodeManagerActorCommand;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Join;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NodeManagerActorWithClusterTest {

    private static ActorTestKit testKit;
    private static TestProbe<ChatChannelEntityCommand> sharedEntityProbe;

    @BeforeAll
    static void setup() {
        Config config = ConfigFactory.load();

        testKit = ActorTestKit.create(config);

        Cluster cluster = Cluster.get(testKit.system());
        cluster.manager().tell(new Join(cluster.selfMember().address()));

        ClusterSharding clusterSharding = ClusterSharding.get(testKit.system());
        sharedEntityProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);

        clusterSharding.init(
                Entity.of(
                        ChatChannelEntity.ENTITY_TYPE_KEY,
                        entityContext -> Behaviors.receiveMessage(message -> {
                            sharedEntityProbe.ref().tell(message);
                            return Behaviors.same();
                        })
                )
        );
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void RegisterSession_메시지를_받으면_ClientSessionActor를_생성하고_ChatChannelEntity에_RequestJoin_메시지를_전송한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ActorRef<NodeManagerActorCommand> nodeManagerActor = testKit.spawn(
                NodeManagerActor.create()
        );

        Long channelId = 1L;
        Long userId = 100L;

        // when
        nodeManagerActor.tell(new NodeManagerActor.RegisterSession(mockClientMessageSender, channelId, userId));

        // then
        RequestJoin requestJoin = sharedEntityProbe.expectMessageClass(
                RequestJoin.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(requestJoin.userId()).isEqualTo(userId),
                () -> assertThat(requestJoin.clientRef()).isNotNull(),
                () -> assertThat(requestJoin.replyTo()).isEqualTo(nodeManagerActor)
        );
    }
}
