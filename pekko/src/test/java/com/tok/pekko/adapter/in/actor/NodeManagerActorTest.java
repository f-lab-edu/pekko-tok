package com.tok.pekko.adapter.in.actor;

import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.model.ChatMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncNewCommand;
import com.tok.pekko.domain.chat.port.in.NodeManagerProtocol.CreateReader;
import com.tok.pekko.domain.chat.port.in.NodeManagerProtocol.NodeManagerActorCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverCommand;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NodeManagerActorTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        Config config = ConfigFactory.load();

        testKit = ActorTestKit.create(config);
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void CreateReader_메시지를_받으면_ChatChannelReaderActor를_생성하고_ChatChannelEntity에게_RegisterReader_메시지를_전송한다() {
        // given
        TestProbe<ChatChannelEntityCommand> entityProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);

        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ActorRef<NodeManagerActorCommand> nodeManagerActor = testKit.spawn(
                NodeManagerActor.create(mockMessageStoragePort)
        );

        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        Long channelId = 2L;
        Long userId = 200L;
        ChatMessages messages = new ChatMessages();

        // when
        nodeManagerActor.tell(new CreateReader(
                messages,
                clientSessionProbe.ref(),
                channelId,
                userId,
                entityProbe.ref()
        ));

        // then
        RegisterReader registerReader = entityProbe.expectMessageClass(
                RegisterReader.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(registerReader.userId()).isEqualTo(userId),
                () -> assertThat(registerReader.reader()).isNotNull()
        );
    }

    @Test
    void TerminateSession_메시지를_받으면_ChatChannelReaderActor에_Shutdown_메시지를_전송하고_ChatChannelEntity에_RemoveReaderSession을_전송한다() {
        // given
        TestProbe<ChatChannelEntityCommand> entityProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);

        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ActorRef<NodeManagerActorCommand> nodeManagerActor = testKit.spawn(
                NodeManagerActor.create(mockMessageStoragePort)
        );

        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        Long channelId = 3L;
        Long userId = 300L;
        ChatMessages messages = new ChatMessages();

        nodeManagerActor.tell(new CreateReader(
                messages,
                clientSessionProbe.ref(),
                channelId,
                userId,
                entityProbe.ref()
        ));

        RegisterReader registerReader = entityProbe.expectMessageClass(
                RegisterReader.class,
                Duration.ofSeconds(3)
        );
        ActorRef<ChatChannelReaderCommand> readerRef = registerReader.reader();

        // when
        nodeManagerActor.tell(new NodeManagerActor.TerminateSession(channelId, userId));

        // then
        TestProbe<Void> terminationProbe = testKit.createTestProbe();
        terminationProbe.expectTerminated(readerRef, Duration.ofSeconds(3));
    }

    @Test
    void TerminateSession_메시지를_받았을_때_등록된_reader가_없으면_아무_동작도_하지_않는다() {
        // given
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ActorRef<NodeManagerActorCommand> nodeManagerActor = testKit.spawn(
                NodeManagerActor.create(mockMessageStoragePort)
        );

        TestProbe<Void> terminationProbe = testKit.createTestProbe();

        Long channelId = 999L;
        Long userId = 999L;

        // when
        nodeManagerActor.tell(new NodeManagerActor.TerminateSession(channelId, userId));

        // then
        terminationProbe.expectNoMessage(Duration.ofMillis(500));

        assertThat(nodeManagerActor).isNotNull();
    }

    @Test
    void Shutdown_메시지를_받으면_모든_ChatChannelReaderActor에_Shutdown_메시지를_전송하고_NodeManagerActor가_종료된다() {
        // given
        TestProbe<ChatChannelEntityCommand> entityProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);

        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ActorRef<NodeManagerActorCommand> nodeManagerActor = testKit.spawn(
                NodeManagerActor.create(mockMessageStoragePort)
        );

        TestProbe<ClientSessionCommand> clientSession1Probe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSession2Probe = testKit.createTestProbe(ClientSessionCommand.class);

        ChatMessages messages1 = new ChatMessages();
        ChatMessages messages2 = new ChatMessages();

        nodeManagerActor.tell(new CreateReader(
                messages1,
                clientSession1Probe.ref(),
                4L,
                400L,
                entityProbe.ref()
        ));

        nodeManagerActor.tell(new CreateReader(
                messages2,
                clientSession2Probe.ref(),
                5L,
                500L,
                entityProbe.ref()
        ));

        RegisterReader reader1 = entityProbe.expectMessageClass(
                RegisterReader.class,
                Duration.ofSeconds(3)
        );
        RegisterReader reader2 = entityProbe.expectMessageClass(
                RegisterReader.class,
                Duration.ofSeconds(3)
        );

        // when
        nodeManagerActor.tell(new NodeManagerActor.Shutdown());

        // then
        TestProbe<Void> terminationProbe = testKit.createTestProbe();
        terminationProbe.expectTerminated(reader1.reader(), Duration.ofSeconds(3));
        terminationProbe.expectTerminated(reader2.reader(), Duration.ofSeconds(3));
        terminationProbe.expectTerminated(nodeManagerActor, Duration.ofSeconds(3));
    }

    @Test
    void 여러_CreateReader_메시지를_연속으로_받으면_모든_reader가_생성되고_등록된다() {
        // given
        TestProbe<ChatChannelEntityCommand> entityProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);

        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ActorRef<NodeManagerActorCommand> nodeManagerActor = testKit.spawn(
                NodeManagerActor.create(mockMessageStoragePort)
        );

        TestProbe<ClientSessionCommand> clientSession1Probe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSession2Probe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSession3Probe = testKit.createTestProbe(ClientSessionCommand.class);

        ChatMessages messages1 = new ChatMessages();
        ChatMessages messages2 = new ChatMessages();
        ChatMessages messages3 = new ChatMessages();

        // when
        nodeManagerActor.tell(new CreateReader(messages1, clientSession1Probe.ref(), 6L, 600L, entityProbe.ref()));
        nodeManagerActor.tell(new CreateReader(messages2, clientSession2Probe.ref(), 6L, 601L, entityProbe.ref()));
        nodeManagerActor.tell(new CreateReader(messages3, clientSession3Probe.ref(), 7L, 600L, entityProbe.ref()));

        // then
        RegisterReader reader1 = entityProbe.expectMessageClass(RegisterReader.class, Duration.ofSeconds(3));
        assertThat(reader1.userId()).isEqualTo(600L);

        RegisterReader reader2 = entityProbe.expectMessageClass(RegisterReader.class, Duration.ofSeconds(3));
        assertThat(reader2.userId()).isEqualTo(601L);

        RegisterReader reader3 = entityProbe.expectMessageClass(RegisterReader.class, Duration.ofSeconds(3));
        assertThat(reader3.userId()).isEqualTo(600L);
    }

    @Test
    void 같은_channelId와_userId로_CreateReader를_호출하면_기존_reader를_덮어쓴다() {
        // given
        TestProbe<ChatChannelEntityCommand> entityProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);

        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ActorRef<NodeManagerActorCommand> nodeManagerActor = testKit.spawn(
                NodeManagerActor.create(mockMessageStoragePort)
        );

        TestProbe<ClientSessionCommand> clientSession1Probe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSession2Probe = testKit.createTestProbe(ClientSessionCommand.class);

        Long channelId = 8L;
        Long userId = 800L;
        ChatMessages messages1 = new ChatMessages();
        ChatMessages messages2 = new ChatMessages();

        // when
        nodeManagerActor.tell(new CreateReader(messages1, clientSession1Probe.ref(), channelId, userId, entityProbe.ref()));
        RegisterReader reader1 = entityProbe.expectMessageClass(RegisterReader.class, Duration.ofSeconds(3));
        ActorRef<ChatChannelReaderCommand> firstReaderRef = reader1.reader();

        nodeManagerActor.tell(new CreateReader(messages2, clientSession2Probe.ref(), channelId, userId, entityProbe.ref()));
        RegisterReader reader2 = entityProbe.expectMessageClass(RegisterReader.class, Duration.ofSeconds(3));
        ActorRef<ChatChannelReaderCommand> secondReaderRef = reader2.reader();

        // then
        assertAll(
                () -> assertThat(firstReaderRef).isNotNull(),
                () -> assertThat(secondReaderRef).isNotNull(),
                () -> assertThat(firstReaderRef).isNotEqualTo(secondReaderRef)
        );

        TestProbe<Void> terminationProbe = testKit.createTestProbe();
        terminationProbe.expectTerminated(firstReaderRef, Duration.ofSeconds(3));

        secondReaderRef.tell(
                new SyncNewCommand(
                        ChatMessage.create(
                        channelId,
                        userId,
                        1L,
                        "test",
                        LocalDateTime.now()
                        )
                )
        );

        clientSession2Probe.expectMessageClass(
                DeliverCommand.class,
                Duration.ofSeconds(3)
        );
    }
}
