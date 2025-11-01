package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ChannelReaderRegistryActor.GetChannelReaderActorRef;
import com.tok.pekko.adapter.out.websocket.ClientSessionActor.FoundChannelReaders;
import com.tok.pekko.domain.chat.model.ChatChannelEntity;
import com.tok.pekko.domain.chat.model.ChatMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.PongHealthCheck;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.SpawnedChannelReaderActor;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

        // when
        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
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

        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
                        clientSessionProbe.ref()
                )
        );
        FoundChannelReaders firstFoundReaders = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        ActorRef<ChatChannelReaderCommand> firstReaderRef = firstFoundReaders.chatChannelReaderRefs()
                                                                             .get(channelId);

        // when & then
        Awaitility.await()
                  .atMost(java.time.Duration.ofSeconds(5))
                  .pollInterval(java.time.Duration.ofMillis(100))
                  .untilAsserted(() -> {
                      registryActor.tell(
                              new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                                      List.of(channelId),
                                      clientSessionProbe.ref()
                              )
                      );
                      FoundChannelReaders actual = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                              ClientSessionCommand.class,
                              Duration.ofSeconds(1)
                      );
                      assertThat(actual.chatChannelReaderRefs()).containsEntry(channelId, firstReaderRef);
                  });
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

        // when
        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId1, channelId2, channelId3),
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

        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
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

        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
                        clientSessionProbe.ref()
                )
        );
        FoundChannelReaders foundReaders = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        ActorRef<ChatChannelReaderCommand> readerRef = foundReaders.chatChannelReaderRefs().get(channelId);

        TestProbe<Void> terminationProbe = testKit.createTestProbe();

        // when
        readerRef.tell(new ChatChannelReaderProtocol.Shutdown());

        terminationProbe.expectTerminated(readerRef, Duration.ofSeconds(5));

        // then
        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(channelId),
                        clientSessionProbe.ref()
                )
        );
        FoundChannelReaders newFoundReaders = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        ActorRef<ChatChannelReaderCommand> newReaderRef = newFoundReaders.chatChannelReaderRefs().get(channelId);

        assertAll(
                () -> assertThat(newReaderRef).isNotEqualTo(readerRef),
                () -> assertThat(newReaderRef.path().name()).startsWith("chat-channel-reader-")
        );
    }

    @Test
    void GetChannelReaderActorRef_메시지를_받았을_때_채널_목록이_비어있으면_빈_Map을_반환한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding)
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        // when
        registryActor.tell(
                new ChannelReaderRegistryActor.GetChannelReaderActorRef(
                        List.of(),
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

    @Test
    void SpawnedChannelReaderActor_메시지를_받으면_readers_맵에_저장하고_RegisterReader_메시지를_ChatChannelEntity에_전송한다() {
        // given
        ClusterSharding mockClusterSharding = mock(ClusterSharding.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> mockEntityRef = mock(EntityRef.class);

        Mockito.doReturn(mockEntityRef)
               .when(mockClusterSharding)
               .entityRefFor(any(), anyString());
        Mockito.doNothing().when(mockEntityRef).tell(any(ChatChannelEntityCommand.class));

        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(mockClusterSharding)
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ChatChannelReaderCommand> readerActorProbe = testKit.createTestProbe(ChatChannelReaderCommand.class);
        Long channelId = 100L;
        String readerName = "test-reader-name";

        // when
        registryActor.tell(
                new SpawnedChannelReaderActor(
                        channelId,
                        readerActorProbe.ref(),
                        readerName
                )
        );

        // then
        registryActor.tell(new GetChannelReaderActorRef(List.of(channelId), clientSessionProbe.ref()));

        FoundChannelReaders actual = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        assertThat(actual.chatChannelReaderRefs()).containsEntry(channelId, readerActorProbe.ref());

        ArgumentCaptor<ChatChannelEntityCommand> captor = ArgumentCaptor.forClass(ChatChannelEntityCommand.class);

        verify(mockEntityRef).tell(captor.capture());

        RegisterReader capturedCommand = (RegisterReader) captor.getValue();

        assertThat(capturedCommand)
                .extracting(RegisterReader::readerName, RegisterReader::reader)
                .containsExactly(readerName, readerActorProbe.ref());
    }
}
