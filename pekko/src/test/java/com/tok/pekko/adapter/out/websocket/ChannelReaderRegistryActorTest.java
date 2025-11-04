package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ChannelReaderRegistryActor.GetChannelReaderActor;
import com.tok.pekko.adapter.out.websocket.ClientSessionActor.FoundChannelReaders;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.actor.ChatMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ReleaseChannelReaderActor;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ReportUnhealthyChannelReader;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ReportUnhealthyClientSession;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.UnregisterChannelReader;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelReaderRegistryActorTest {

    private static ActorTestKit testKit;
    private static ClusterSharding clusterSharding;
    private static MessageStoragePort mockMessageStoragePort;

    @BeforeAll
    static void setup() {
        testKit = ActorTestKit.create(ConfigFactory.load());

        mockMessageStoragePort = mock(MessageStoragePort.class);

        clusterSharding = ClusterSharding.get(testKit.system());
        clusterSharding.init(
                Entity.of(
                        ChannelEntity.ENTITY_TYPE_KEY,
                        entityContext -> ChannelEntity.create(
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
    void GetChannelReaderActor_메시지를_받으면_새로운_ChatChannelReaderActor를_생성하고_ClientSession에_전달한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding, Duration.ofSeconds(30L))
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long userId = 100L;
        Long channelId = 1L;

        // when
        registryActor.tell(
                new GetChannelReaderActor(
                        userId,
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
    void GetChannelReaderActor_메시지로_같은_채널을_요청하면_이미_생성된_ChatChannelReaderActor를_반환한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding, Duration.ofSeconds(30L))
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long userId = 101L;
        Long channelId = 2L;

        registryActor.tell(
                new GetChannelReaderActor(
                        userId,
                        List.of(channelId),
                        clientSessionProbe.ref()
                )
        );
        FoundChannelReaders firstFoundReaders = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );
        ActorRef<ChannelReaderCommand> firstReaderRef = firstFoundReaders.chatChannelReaderRefs()
                                                                         .get(channelId);

        // when & then
        Awaitility.await()
                  .atMost(Duration.ofSeconds(5))
                  .pollInterval(Duration.ofMillis(100))
                  .untilAsserted(() -> {
                      registryActor.tell(
                              new GetChannelReaderActor(
                                      userId,
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
    void GetChannelReaderActor_메시지를_받았을_때_메시지의_채널_목록이_비어있으면_빈_Map을_반환한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding, Duration.ofSeconds(30L))
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long userId = 102L;

        // when
        registryActor.tell(
                new GetChannelReaderActor(
                        userId,
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
    void SpawnedChannelReaderActor_메시지를_받으면_readers_맵에_저장하고_RegisterReader_메시지를_ChannelEntity에_전송한다() {
        // given
        ClusterSharding mockClusterSharding = mock(ClusterSharding.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEntityCommand> mockEntityRef = mock(EntityRef.class);

        doReturn(mockEntityRef).when(mockClusterSharding).entityRefFor(any(), anyString());
        doNothing().when(mockEntityRef).tell(any(ChannelEntityCommand.class));

        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(mockClusterSharding, Duration.ofSeconds(30L))
        );

        Long userId = 103L;
        Long channelId = 100L;
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        // when
        registryActor.tell(new GetChannelReaderActor(userId, List.of(channelId), clientSessionProbe.ref()));

        FoundChannelReaders foundReaders = (FoundChannelReaders) clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );

        ActorRef<ChannelReaderCommand> actualReaderRef = foundReaders.chatChannelReaderRefs().get(channelId);

        // then
        assertThat(actualReaderRef).isNotNull();

        ArgumentCaptor<ChannelEntityCommand> captor = ArgumentCaptor.forClass(ChannelEntityCommand.class);

        verify(mockEntityRef, times(2)).tell(captor.capture());

        RegisterReader capturedCommand = (RegisterReader) captor.getValue();

        assertThat(capturedCommand)
                .extracting(RegisterReader::reader)
                .isEqualTo(actualReaderRef);
    }

    @Test
    void ReleaseChannelReaderActor_메시지를_받으면_해당_clientSession을_reader의_clientSessions에서_제거한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding, Duration.ofSeconds(30L))
        );
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);
        Long userId1 = 104L;
        Long userId2 = 105L;
        Long channelId = 10L;

        registryActor.tell(
                new GetChannelReaderActor(
                        userId1,
                        List.of(channelId),
                        clientSessionProbe1.ref()
                )
        );
        clientSessionProbe1.expectMessageClass(ClientSessionCommand.class, Duration.ofSeconds(5));

        registryActor.tell(
                new GetChannelReaderActor(
                        userId2,
                        List.of(channelId),
                        clientSessionProbe2.ref()
                )
        );
        clientSessionProbe2.expectMessageClass(ClientSessionCommand.class, Duration.ofSeconds(5));

        // when
        registryActor.tell(
                new ReleaseChannelReaderActor(
                        userId1,
                        List.of(channelId)
                )
        );

        // then
        Awaitility.await()
                  .atMost(Duration.ofSeconds(5))
                  .pollInterval(Duration.ofMillis(100))
                  .untilAsserted(() -> {
                      TestProbe<ClientSessionCommand> newClientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
                      registryActor.tell(
                              new GetChannelReaderActor(
                                      userId1,
                                      List.of(channelId),
                                      newClientSessionProbe.ref()
                              )
                      );
                      FoundChannelReaders actual = (FoundChannelReaders) newClientSessionProbe.expectMessageClass(
                              ClientSessionCommand.class,
                              Duration.ofSeconds(1)
                      );
                      assertThat(actual.chatChannelReaderRefs()).containsKey(channelId);
                  });
    }

    @Test
    void ReportUnhealthyChannelReader_메시지를_받으면_unhealthy_reader를_정지하고_모든_clientSession에_UnregisterChannelReader_메시지를_전송한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding, Duration.ofSeconds(30L))
        );
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        Long userId = 106L;
        Long channelId = 11L;

        registryActor.tell(
                new GetChannelReaderActor(
                        userId,
                        List.of(channelId),
                        clientSessionProbe.ref()
                )
        );
        clientSessionProbe.expectMessageClass(
                ClientSessionCommand.class,
                Duration.ofSeconds(5)
        );

        Awaitility.await()
                  .atMost(Duration.ofSeconds(1))
                  .pollInterval(Duration.ofMillis(100))
                  .untilAsserted(() -> {
                      // when
                      registryActor.tell(
                              new ReportUnhealthyChannelReader(List.of(channelId))
                      );

                      // then
                      clientSessionProbe.expectMessageClass(
                              UnregisterChannelReader.class,
                              Duration.ofSeconds(1)
                      );
                  });
    }

    @Test
    void ReportUnhealthyClientSession_메시지를_받으면_해당_userId_clientSession을_reader의_clientSessions에서_제거한다() {
        // given
        ActorRef<ChannelReaderRegistryCommand> registryActor = testKit.spawn(
                ChannelReaderRegistryActor.create(clusterSharding, Duration.ofSeconds(30L))
        );
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);
        Long userId1 = 107L;
        Long userId2 = 108L;
        Long channelId = 12L;

        registryActor.tell(
                new GetChannelReaderActor(
                        userId1,
                        List.of(channelId),
                        clientSessionProbe1.ref()
                )
        );
        clientSessionProbe1.expectMessageClass(ClientSessionCommand.class, Duration.ofSeconds(5));

        registryActor.tell(
                new GetChannelReaderActor(
                        userId2,
                        List.of(channelId),
                        clientSessionProbe2.ref()
                )
        );
        clientSessionProbe2.expectMessageClass(ClientSessionCommand.class, Duration.ofSeconds(5));

        // when
        registryActor.tell(
                new ReportUnhealthyClientSession(userId1, channelId)
        );

        // then
        Awaitility.await()
                  .atMost(Duration.ofSeconds(5))
                  .pollInterval(Duration.ofMillis(100))
                  .untilAsserted(() -> {
                      TestProbe<ClientSessionCommand> newClientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
                      registryActor.tell(
                              new GetChannelReaderActor(
                                      userId1,
                                      List.of(channelId),
                                      newClientSessionProbe.ref()
                              )
                      );
                      FoundChannelReaders actual = (FoundChannelReaders) newClientSessionProbe.expectMessageClass(
                              ClientSessionCommand.class,
                              Duration.ofSeconds(1)
                      );
                      assertThat(actual.chatChannelReaderRefs()).containsKey(channelId);
                  });
    }

}
