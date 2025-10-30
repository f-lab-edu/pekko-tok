package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ClientSessionActor.FoundChannelReaders;
import com.tok.pekko.domain.chat.model.ChatChannelEntity;
import com.tok.pekko.domain.chat.model.ChatMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.PongHealthCheck;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelReaderRegistryActorTest {

    private static ActorTestKit testKit;
    private static ClusterSharding clusterSharding;
    private static MessageStoragePort mockMessageStoragePort;

    @BeforeAll
    static void setup() {
        Config config = ConfigFactory.load();
        testKit = ActorTestKit.create(config);

        mockMessageStoragePort = mock(MessageStoragePort.class);

        clusterSharding = ClusterSharding.get(testKit.system());
        clusterSharding.init(
                Entity.of(
                        ChatChannelEntity.ENTITY_TYPE_KEY,
                        entityContext -> ChatChannelEntity.create(
                                Clock.systemDefaultZone(),
                                Long.parseLong(entityContext.getEntityId()),
                                new ChatMessages(),
                                mockMessageStoragePort
                        )
                )
        );
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void GetChannelReaderActorRef_메시지를_받으면_새로운_ChatChannelReaderActor를_생성하고_ClientSession에_전달한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding)
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long channelId = 1L;
        Long userId = 100L;

        // when
        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
                        userId,
                        clientSessionProbe.ref()
                )
        );

        // then
        FoundChannelReaders actual = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );

        assertThat(actual.chatChannelReaderRefs()).containsKey(channelId)
                                                  .extracting(readers -> readers.get(channelId))
                                                  .isNotNull();
    }

    @Test
    void GetChannelReaderActorRef_메시지로_같은_채널을_요청하면_이미_생성된_ChatChannelReaderActor를_반환한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding)
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long channelId = 2L;
        Long userId = 100L;

        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
                        userId,
                        clientSessionProbe.ref()
                )
        );
        FoundChannelReaders firstFoundReaders = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        ActorRef<ChatChannelReaderCommand> firstReaderRef = firstFoundReaders.chatChannelReaderRefs()
                                                                             .get(channelId);

        // when
        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
                        userId,
                        clientSessionProbe.ref()
                )
        );

        // then
        FoundChannelReaders actual = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        assertThat(actual).extracting(target -> target.chatChannelReaderRefs().get(channelId))
                          .isEqualTo(firstReaderRef);
    }

    @Test
    void GetChannelReaderActorRef_메시지로_여러_채널을_요청하면_각_채널별_ChatChannelReaderActor를_생성한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding)
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long channelId1 = 3L;
        Long channelId2 = 4L;
        Long channelId3 = 5L;
        Long userId = 100L;

        // when
        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId1, channelId2, channelId3),
                        userId,
                        clientSessionProbe.ref()
                )
        );

        // then
        FoundChannelReaders actual = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        assertThat(actual.chatChannelReaderRefs()).containsKeys(channelId1, channelId2, channelId3)
                                                  .hasSize(3);
    }

    @Test
    void PongHealthCheck_메시지를_받으면_해당_채널의_타임아웃_스케줄을_취소한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding)
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long channelId = 6L;
        Long userId = 100L;

        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
                        userId,
                        clientSessionProbe.ref()
                )
        );
        clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );

        // when
        registryActor.tell(new PongHealthCheck(channelId));

        // then
        testKit.createTestProbe().expectNoMessage(Duration.ofMillis(100));
    }

    @Test
    void ChatChannelReaderActor가_종료되면_Terminated_신호를_받아_Registry에서_제거한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding)
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long channelId = 7L;
        Long userId = 100L;

        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
                        userId,
                        clientSessionProbe.ref()
                )
        );
        FoundChannelReaders foundReaders = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        ActorRef<ChatChannelReaderCommand> readerRef = foundReaders.chatChannelReaderRefs()
                                                                   .get(channelId);

        TestProbe<Void> terminationProbe = testKit.createTestProbe();

        // when
        readerRef.tell(new com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.Shutdown());

        clientSessionProbe.expectMessageClass(
                com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown.class,
                Duration.ofSeconds(5)
        );

        terminationProbe.expectTerminated(readerRef, Duration.ofSeconds(5));

        // then
        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
                        userId,
                        clientSessionProbe.ref()
                )
        );
        FoundChannelReaders actual = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        assertThat(actual).extracting(target -> target.chatChannelReaderRefs().get(channelId))
                          .isNotEqualTo(readerRef);
    }

    @Test
    void 여러_ChatChannelReaderActor_중_하나가_종료되어도_나머지는_유지된다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding)
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long channelId1 = 8L;
        Long channelId2 = 9L;
        Long userId = 100L;

        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId1, channelId2),
                        userId,
                        clientSessionProbe.ref()
                )
        );
        ClientSessionCommand response = clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        FoundChannelReaders foundReaders = (FoundChannelReaders) response;
        ActorRef<ChatChannelReaderCommand> readerRef1 = foundReaders.chatChannelReaderRefs().get(channelId1);
        ActorRef<ChatChannelReaderCommand> readerRef2 = foundReaders.chatChannelReaderRefs().get(channelId2);

        TestProbe<Void> terminationProbe = testKit.createTestProbe();

        // when
        readerRef1.tell(new com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.Shutdown());

        clientSessionProbe.expectMessageClass(
                com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown.class,
                Duration.ofSeconds(5)
        );

        terminationProbe.expectTerminated(readerRef1, Duration.ofSeconds(5));

        // then
        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId1, channelId2),
                        userId,
                        clientSessionProbe.ref()
                )
        );
        FoundChannelReaders newFoundReaders = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        ActorRef<ChatChannelReaderCommand> newReaderRef1 = newFoundReaders.chatChannelReaderRefs().get(channelId1);
        ActorRef<ChatChannelReaderCommand> newReaderRef2 = newFoundReaders.chatChannelReaderRefs().get(channelId2);

        assertAll(
                () -> assertThat(newReaderRef1).isNotEqualTo(readerRef1),
                () -> assertThat(newReaderRef2).isEqualTo(readerRef2)
        );
    }

    @Test
    void GetChannelReaderActorRef_메시지를_받았을_때_채널_목록이_비어있으면_빈_Map을_반환한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding)
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long userId = 100L;

        // when
        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(),
                        userId,
                        clientSessionProbe.ref()
                )
        );

        // then
        FoundChannelReaders actual = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        assertThat(actual.chatChannelReaderRefs()).isEmpty();
    }
}
