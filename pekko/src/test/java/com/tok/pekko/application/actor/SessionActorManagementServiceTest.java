package com.tok.pekko.application.actor;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.model.ChatChannelEntity;
import com.tok.pekko.domain.chat.model.ChatMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.global.actor.GuardianActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Clock;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Join;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SessionActorManagementServiceTest {

    private static Config config;
    private ActorTestKit testKit;
    private ActorSystem<GuardianActor.GuardianCommand> actorSystem;
    private SessionActorManagementService service;
    private ConcurrentHashMap<NodeReaderKey, ActorRef<ChatChannelReaderCommand>> localChatChannelReaders;

    @BeforeAll
    static void setup() {
        config = ConfigFactory.load();
    }

    @BeforeEach
    void beforeEach() {
        testKit = ActorTestKit.create(config);
        actorSystem = ActorSystem.create(
                GuardianActor.create(),
                "test-system",
                config
        );

        Cluster cluster = Cluster.get(actorSystem);
        cluster.manager().tell(new Join(cluster.selfMember().address()));

        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        doNothing().when(mockMessageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ClusterSharding clusterSharding = ClusterSharding.get(actorSystem);
        clusterSharding.init(
                Entity.of(
                        ChatChannelEntity.ENTITY_TYPE_KEY,
                        entityContext -> ChatChannelEntity.create(
                                Clock.systemDefaultZone(),
                                Long.valueOf(entityContext.getEntityId()),
                                new ChatMessages(),
                                mockMessageStoragePort
                        )
                )
        );

        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);

        localChatChannelReaders = new ConcurrentHashMap<>();
        service = new SessionActorManagementService(
                clusterSharding,
                mockChannelMembershipPort,
                actorSystem,
                localChatChannelReaders
        );
    }

    @AfterEach
    void afterEach() {
        testKit.shutdownTestKit();
        actorSystem.terminate();
    }

    @Test
    void registerSession을_호출하면_ClientSessionActor와_ChatChannelReaderActor가_생성되고_localChatChannelReaders에_등록된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        Long channelId = 1L;
        Long userId = 100L;
        NodeReaderKey key = new NodeReaderKey(channelId, userId);

        // when
        service.registerSession(mockClientMessageSender, userId, channelId);

        // then
        TestProbe<Void> delayProbe = testKit.createTestProbe();
        delayProbe.expectNoMessage(Duration.ofSeconds(2));

        assertAll(
                () -> assertThat(localChatChannelReaders).containsKey(key),
                () -> assertThat(localChatChannelReaders.get(key)).isNotNull()
        );
    }

    @Test
    void registerSession을_호출하면_localChatChannelReaders_맵에_reader가_등록된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        Long channelId = 2L;
        Long userId = 200L;

        // when
        service.registerSession(mockClientMessageSender, userId, channelId);

        // then
        TestProbe<Void> delayProbe = testKit.createTestProbe();
        delayProbe.expectNoMessage(Duration.ofSeconds(2));

        NodeReaderKey key = new NodeReaderKey(channelId, userId);
        assertAll(
                () -> assertThat(localChatChannelReaders).containsKey(key),
                () -> assertThat(localChatChannelReaders.get(key)).isNotNull()
        );
    }

    @Test
    void terminateSession을_호출하면_ChatChannelReaderActor가_종료되고_localChatChannelReaders에서_제거된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        Long channelId = 3L;
        Long userId = 300L;
        NodeReaderKey key = new NodeReaderKey(channelId, userId);

        service.registerSession(mockClientMessageSender, userId, channelId);

        TestProbe<Void> delayProbe = testKit.createTestProbe();
        delayProbe.expectNoMessage(Duration.ofSeconds(2));

        ActorRef<ChatChannelReaderCommand> readerRef = localChatChannelReaders.get(key);
        assertThat(readerRef).isNotNull();

        // when
        service.terminateSession(channelId, userId);

        // then
        TestProbe<Void> terminationProbe = testKit.createTestProbe();
        terminationProbe.expectTerminated(readerRef, Duration.ofSeconds(3));

        assertThat(localChatChannelReaders).doesNotContainKey(key);
    }

    @Test
    void terminateSession을_호출했을_때_등록된_reader가_없으면_아무_동작도_하지_않는다() {
        // given
        Long channelId = 999L;
        Long userId = 999L;

        // when
        service.terminateSession(channelId, userId);

        // then
        TestProbe<Void> noErrorProbe = testKit.createTestProbe();
        noErrorProbe.expectNoMessage(Duration.ofMillis(500));

        assertThat(localChatChannelReaders).isEmpty();
    }

    @Test
    void 여러_registerSession을_연속으로_호출하면_모든_reader가_생성되고_등록된다() {
        // given
        ClientMessageSender mockClientMessageSender1 = mock(ClientMessageSender.class);
        ClientMessageSender mockClientMessageSender2 = mock(ClientMessageSender.class);
        ClientMessageSender mockClientMessageSender3 = mock(ClientMessageSender.class);

        // when
        service.registerSession(mockClientMessageSender1, 501L, 5L);
        service.registerSession(mockClientMessageSender2, 502L, 5L);
        service.registerSession(mockClientMessageSender3, 501L, 6L);

        // then
        TestProbe<Void> delayProbe = testKit.createTestProbe();
        delayProbe.expectNoMessage(Duration.ofSeconds(3));

        assertAll(
                () -> assertThat(localChatChannelReaders).containsKey(new NodeReaderKey(5L, 501L)),
                () -> assertThat(localChatChannelReaders).containsKey(new NodeReaderKey(5L, 502L)),
                () -> assertThat(localChatChannelReaders).containsKey(new NodeReaderKey(6L, 501L)),
                () -> assertThat(localChatChannelReaders).hasSize(3)
        );
    }

    @Test
    void 같은_channelId와_userId로_registerSession을_호출하면_새로운_reader가_기존_reader를_덮어쓴다() {
        // given
        ClientMessageSender mockClientMessageSender1 = mock(ClientMessageSender.class);
        ClientMessageSender mockClientMessageSender2 = mock(ClientMessageSender.class);
        Long channelId = 7L;
        Long userId = 700L;
        NodeReaderKey key = new NodeReaderKey(channelId, userId);

        // when
        service.registerSession(mockClientMessageSender1, userId, channelId);

        TestProbe<Void> delayProbe = testKit.createTestProbe();
        delayProbe.expectNoMessage(Duration.ofSeconds(2));

        ActorRef<ChatChannelReaderCommand> firstReaderRef = localChatChannelReaders.get(key);

        service.registerSession(mockClientMessageSender2, userId, channelId);

        delayProbe.expectNoMessage(Duration.ofSeconds(2));

        ActorRef<ChatChannelReaderCommand> secondReaderRef = localChatChannelReaders.get(key);

        // then
        assertAll(
                () -> assertThat(firstReaderRef).isNotNull(),
                () -> assertThat(secondReaderRef).isNotNull(),
                () -> assertThat(firstReaderRef).isNotEqualTo(secondReaderRef),
                () -> assertThat(localChatChannelReaders).hasSize(1),
                () -> assertThat(localChatChannelReaders).containsEntry(key, secondReaderRef)
        );
    }

    @Test
    void 여러_세션을_등록하고_일부만_terminateSession을_호출하면_해당_reader만_제거된다() {
        // given
        ClientMessageSender mockClientMessageSender1 = mock(ClientMessageSender.class);
        ClientMessageSender mockClientMessageSender2 = mock(ClientMessageSender.class);
        ClientMessageSender mockClientMessageSender3 = mock(ClientMessageSender.class);

        service.registerSession(mockClientMessageSender1, 801L, 8L);
        service.registerSession(mockClientMessageSender2, 802L, 8L);
        service.registerSession(mockClientMessageSender3, 803L, 8L);

        TestProbe<Void> delayProbe = testKit.createTestProbe();
        delayProbe.expectNoMessage(Duration.ofSeconds(3));

        NodeReaderKey key1 = new NodeReaderKey(8L, 801L);
        NodeReaderKey key2 = new NodeReaderKey(8L, 802L);
        NodeReaderKey key3 = new NodeReaderKey(8L, 803L);

        ActorRef<ChatChannelReaderCommand> readerRef2 = localChatChannelReaders.get(key2);

        // when
        service.terminateSession(8L, 802L);

        // then
        TestProbe<Void> terminationProbe = testKit.createTestProbe();
        terminationProbe.expectTerminated(readerRef2, Duration.ofSeconds(3));

        assertAll(
                () -> assertThat(localChatChannelReaders).containsKey(key1),
                () -> assertThat(localChatChannelReaders).doesNotContainKey(key2),
                () -> assertThat(localChatChannelReaders).containsKey(key3),
                () -> assertThat(localChatChannelReaders).hasSize(2)
        );
    }
}
