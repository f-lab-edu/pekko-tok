package com.tok.pekko.domain.chat.model;

import com.tok.pekko.domain.chat.model.ChatChannelReaderActor.DeliverSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RequestJoin;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SendMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncRecentMessages;
import com.tok.pekko.domain.chat.model.ChatChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncNewCommand;
import com.tok.pekko.domain.chat.port.out.NodeManagerProtocol.CreateReader;
import com.tok.pekko.domain.chat.port.out.NodeManagerProtocol.NodeManagerActorCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatChannelEntityTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void teardown() {
        testKit.shutdownTestKit();
    }

    @Test
    void RequestJoin_메시지를_받으면_CreateReader_메시지를_replyTo에_전달한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        TestProbe<NodeManagerActorCommand> replyProbe = testKit.createTestProbe();
        TestProbe<ClientSessionCommand> clientProbe = testKit.createTestProbe();
        Long userId = 100L;

        // when
        channelEntity.tell(new RequestJoin(
                userId,
                clientProbe.ref(),
                replyProbe.ref()
        ));

        // then
        CreateReader createReader = replyProbe.expectMessageClass(CreateReader.class);
        assertAll(
                () -> assertThat(createReader.channelId()).isEqualTo(channelId),
                () -> assertThat(createReader.userId()).isEqualTo(userId),
                () -> assertThat(createReader.clientActorRef()).isEqualTo(clientProbe.ref()),
                () -> assertThat(createReader.replyTo()).isEqualTo(channelEntity)
        );
    }

    @Test
    void RegisterReader_메시지로_새로운_reader를_등록하면_기존_reader에게_메시지를_전달하지_않는다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        TestProbe<ChatChannelReaderCommand> readerProbe = testKit.createTestProbe();
        Long userId = 100L;

        // when
        channelEntity.tell(new RegisterReader(userId, readerProbe.ref()));

        // then
        readerProbe.expectNoMessage(Duration.ofMillis(200));
    }

    @Test
    void RegisterReader_메시지로_기존_reader를_교체하면_기존_reader에게_Shutdown_메시지를_전달한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        TestProbe<ChatChannelReaderCommand> oldReaderProbe = testKit.createTestProbe();
        TestProbe<ChatChannelReaderCommand> newReaderProbe = testKit.createTestProbe();
        Long userId = 100L;

        // when
        channelEntity.tell(new RegisterReader(userId, oldReaderProbe.ref()));
        oldReaderProbe.expectNoMessage(Duration.ofMillis(200));

        channelEntity.tell(new RegisterReader(userId, newReaderProbe.ref()));

        // then
        Shutdown shutdown = oldReaderProbe.expectMessageClass(Shutdown.class);
        assertThat(shutdown).isNotNull();

        newReaderProbe.expectNoMessage(Duration.ofMillis(200));
    }

    @Test
    void SyncPersistedMessage_메시지를_받으면_모든_reader에게_SyncNewCommand_메시지를_전달한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        TestProbe<ChatChannelReaderCommand> reader1Probe = testKit.createTestProbe();
        TestProbe<ChatChannelReaderCommand> reader2Probe = testKit.createTestProbe();

        channelEntity.tell(new RegisterReader(100L, reader1Probe.ref()));
        channelEntity.tell(new RegisterReader(200L, reader2Probe.ref()));

        Long userId = 100L;
        String messageContent = "Hello, World!";
        LocalDateTime timestamp = LocalDateTime.of(2025, 10, 17, 12, 0, 0);

        ChatMessage persistedMessage = new ChatMessage(
                channelId,
                1L,
                userId,
                1L,
                messageContent,
                timestamp
        );

        // when
        channelEntity.tell(new SyncPersistedMessage(persistedMessage));

        // then
        SyncNewCommand syncCommand1 = reader1Probe.expectMessageClass(SyncNewCommand.class);
        assertThat(syncCommand1.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.channelId()).isEqualTo(channelId),
                        () -> assertThat(message.userId()).isEqualTo(userId),
                        () -> assertThat(message.message()).isEqualTo(messageContent),
                        () -> assertThat(message.messageSequence()).isEqualTo(1L),
                        () -> assertThat(message.timestamp()).isEqualTo(timestamp)
                ));

        SyncNewCommand syncCommand2 = reader2Probe.expectMessageClass(SyncNewCommand.class);
        assertThat(syncCommand2.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.channelId()).isEqualTo(channelId),
                        () -> assertThat(message.userId()).isEqualTo(userId),
                        () -> assertThat(message.message()).isEqualTo(messageContent),
                        () -> assertThat(message.messageSequence()).isEqualTo(1L),
                        () -> assertThat(message.timestamp()).isEqualTo(timestamp)
                ));
    }

    @Test
    void RemoveReaderSession_메시지를_받으면_해당_reader가_ChatChannelEntity에서_제거되어_메시지를_받지_않는다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        TestProbe<ChatChannelReaderCommand> readerProbe = testKit.createTestProbe();
        Long userId = 100L;

        channelEntity.tell(new RegisterReader(userId, readerProbe.ref()));

        // when
        channelEntity.tell(new RemoveShutdownReader(userId));

        Long senderId = 200L;
        String messageContent = "Test message";
        LocalDateTime timestamp = LocalDateTime.of(2025, 10, 17, 12, 0, 0);

        channelEntity.tell(new SendMessage(senderId, messageContent, timestamp));

        // then
        readerProbe.expectNoMessage(Duration.ofMillis(200));
    }

    @Test
    void 다른_userId로_reader를_등록하면_각각_독립적으로_관리되고_같은_userId로_재등록하면_기존_reader는_종료된다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        TestProbe<ChatChannelReaderCommand> reader1Probe = testKit.createTestProbe();
        TestProbe<ChatChannelReaderCommand> reader2Probe = testKit.createTestProbe();
        TestProbe<ChatChannelReaderCommand> reader3Probe = testKit.createTestProbe();

        Long userId1 = 100L;
        Long userId2 = 200L;

        // when
        channelEntity.tell(new RegisterReader(userId1, reader1Probe.ref()));
        channelEntity.tell(new RegisterReader(userId2, reader2Probe.ref()));
        channelEntity.tell(new RegisterReader(userId1, reader3Probe.ref()));

        // then
        Shutdown shutdown = reader1Probe.expectMessageClass(Shutdown.class);
        assertThat(shutdown).isNotNull();

        reader2Probe.expectNoMessage(Duration.ofMillis(200));
        reader3Probe.expectNoMessage(Duration.ofMillis(200));

        LocalDateTime timestamp = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        ChatMessage persistedMessage = new ChatMessage(channelId, 1L, userId1, 1L, "test", timestamp);
        channelEntity.tell(new SyncPersistedMessage(persistedMessage));

        reader1Probe.expectNoMessage(Duration.ofMillis(200));

        SyncNewCommand syncCommand2 = reader2Probe.expectMessageClass(SyncNewCommand.class);
        assertThat(syncCommand2.message())
                .isNotNull()
                .satisfies(message ->
                        assertThat(message.message()).isEqualTo("test")
                );

        SyncNewCommand syncCommand3 = reader3Probe.expectMessageClass(SyncNewCommand.class);
        assertThat(syncCommand3.message())
                .isNotNull()
                .satisfies(message ->
                        assertThat(message.message()).isEqualTo("test")
                );
    }

    @Test
    void SendCommand_메시지를_받으면_MessageStoragePort에_채팅_메시지_저장을_요청한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        Long userId = 100L;
        String messageContent = "Hello, World!";
        LocalDateTime timestamp = LocalDateTime.of(2025, 10, 17, 12, 0, 0);

        // when
        channelEntity.tell(new SendMessage(userId, messageContent, timestamp));

        // then
        verify(messageStoragePort, timeout(1000)).store(any(ChatMessage.class), eq(channelEntity));
    }

    @Test
    void SendCommand_메시지를_받으면_유효한_ChatMessage를_생성하고_저장한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        Long userId = 100L;
        String messageContent = "Test message";
        LocalDateTime timestamp = LocalDateTime.of(2025, 10, 17, 12, 30, 0);

        // when
        channelEntity.tell(new SendMessage(userId, messageContent, timestamp));

        // then
        verify(messageStoragePort, timeout(1000)).store(
                argThat(message ->
                        message.messageId() == null
                                && message.channelId().equals(channelId)
                                && message.userId().equals(userId)
                                && message.messageSequence() > 0
                                && message.message().equals(messageContent)
                                && message.timestamp().equals(timestamp)
                ),
                eq(channelEntity)
        );
    }

    @Test
    void SyncRecentMessages_메시지를_받으면_messages를_동기화한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        List<ChatMessage> recentMessages = List.of(
                new ChatMessage(1L, channelId, 100L, 1L, "Message 1", LocalDateTime.of(2025, 10, 17, 10, 0, 0)),
                new ChatMessage(2L, channelId, 101L, 2L, "Message 2", LocalDateTime.of(2025, 10, 17, 10, 0, 1))
        );

        // when
        channelEntity.tell(new SyncRecentMessages(recentMessages));

        // then
        TestProbe<ChatChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RegisterReader(100L, readerProbe.ref()));

        TestProbe<NodeManagerActorCommand> nodeManagerProbe = testKit.createTestProbe();
        TestProbe<ClientSessionCommand> clientProbe = testKit.createTestProbe();

        channelEntity.tell(new RequestJoin(100L, clientProbe.ref(), nodeManagerProbe.ref()));

        CreateReader createReader = nodeManagerProbe.expectMessageClass(CreateReader.class);
        assertAll(
                () -> assertThat(createReader.messages().getRecentMessages(10)).hasSize(2),
                () -> assertThat(createReader.messages().getRecentMessages(10)).extracting(ChatMessage::message)
                                                                               .containsExactly("Message 2", "Message 1")
        );
    }

    @Test
    void 메시지를_여러_번_동기화하면_messageSequence가_순차적으로_증가한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        TestProbe<ChatChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RegisterReader(100L, readerProbe.ref()));

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 12, 0, 1);
        LocalDateTime timestamp3 = LocalDateTime.of(2025, 10, 17, 12, 0, 2);

        ChatMessage message1 = new ChatMessage(channelId, 1L, 100L, 1L, "First message", timestamp1);
        ChatMessage message2 = new ChatMessage(channelId, 2L, 100L, 2L, "Second message", timestamp2);
        ChatMessage message3 = new ChatMessage(channelId, 3L, 100L, 3L, "Third message", timestamp3);

        // when
        channelEntity.tell(new SyncPersistedMessage(message1));
        channelEntity.tell(new SyncPersistedMessage(message2));
        channelEntity.tell(new SyncPersistedMessage(message3));

        // then
        SyncNewCommand sync1 = readerProbe.expectMessageClass(SyncNewCommand.class);
        assertThat(sync1.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.messageSequence()).isEqualTo(1L),
                        () -> assertThat(message.message()).isEqualTo("First message"),
                        () -> assertThat(message.timestamp()).isEqualTo(timestamp1)
                ));

        SyncNewCommand sync2 = readerProbe.expectMessageClass(SyncNewCommand.class);
        assertThat(sync2.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.messageSequence()).isEqualTo(2L),
                        () -> assertThat(message.message()).isEqualTo("Second message"),
                        () -> assertThat(message.timestamp()).isEqualTo(timestamp2)
                ));

        SyncNewCommand sync3 = readerProbe.expectMessageClass(SyncNewCommand.class);
        assertThat(sync3.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.messageSequence()).isEqualTo(3L),
                        () -> assertThat(message.message()).isEqualTo("Third message"),
                        () -> assertThat(message.timestamp()).isEqualTo(timestamp3)
                ));
    }

    @Test
    void 생성_시_findRecentMessages를_호출한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        // when
        testKit.spawn(ChatChannelEntity.create(
                channelId,
                messages,
                messageStoragePort
        ));

        // then
        verify(messageStoragePort, timeout(1000)).findRecentMessages(eq(channelId), eq(50), any());
    }

    @Test
    void RequestSyncMessages_메시지를_받으면_요청한_reader에게_DeliverSyncMessages를_전달한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChatChannelEntityCommand> channelEntity =
                testKit.spawn(ChatChannelEntity.create(
                        channelId,
                        messages,
                        messageStoragePort
                ));

        TestProbe<ChatChannelReaderCommand> readerProbe = testKit.createTestProbe();

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 12, 0, 1);
        ChatMessage message1 = new ChatMessage(channelId, 1L, 100L, 1L, "Message 1", timestamp1);
        ChatMessage message2 = new ChatMessage(channelId, 2L, 100L, 2L, "Message 2", timestamp2);

        channelEntity.tell(new SyncPersistedMessage(message1));
        channelEntity.tell(new SyncPersistedMessage(message2));

        // when
        channelEntity.tell(new RequestSyncMessages(readerProbe.ref()));

        // then
        DeliverSyncMessages delivered = readerProbe.expectMessageClass(DeliverSyncMessages.class);
        assertAll(
                () -> assertThat(delivered.messages()).hasSize(2),
                () -> assertThat(delivered.messages()).extracting(ChatMessage::message)
                                                      .containsExactly("Message 2", "Message 1")
        );
    }
}
