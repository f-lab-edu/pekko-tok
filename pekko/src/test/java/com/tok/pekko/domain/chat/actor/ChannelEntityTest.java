package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.DeliverSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelBatchPersisted;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelLoaded;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.DeleteMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SendMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncDeletedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncRecentMessages;
import com.tok.pekko.domain.chat.actor.ChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncUpdatedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.UpdateMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
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
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelEntityTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void RegisterReader_메시지로_새로운_reader를_등록하면_기존_reader에게_메시지를_전달하지_않는다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();

        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        readerProbe.expectNoMessage(Duration.ofMillis(200));
    }

    @Test
    void SyncPersistedMessage_메시지를_받으면_모든_reader에게_SyncNewCommand_메시지를_전달한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        TestProbe<ChannelReaderCommand> reader1Probe = testKit.createTestProbe();
        TestProbe<ChannelReaderCommand> reader2Probe = testKit.createTestProbe();

        channelEntity.tell(new RegisterReader("reader1", reader1Probe.ref()));
        channelEntity.tell(new RegisterReader("reader2", reader2Probe.ref()));

        Long userId = 100L;
        String messageContent = "Hello, World!";
        LocalDateTime timestamp = LocalDateTime.of(2025, 10, 17, 12, 0, 0);

        ChatMessage persistedMessage = new ChatMessage(
                1L,
                channelId,
                userId,
                1L,
                messageContent,
                timestamp,
                timestamp
        );

        channelEntity.tell(new SyncPersistedMessage(persistedMessage));

        SyncNewMessage syncCommand1 = reader1Probe.expectMessageClass(SyncNewMessage.class);
        assertThat(syncCommand1.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.channelId()).isEqualTo(channelId),
                        () -> assertThat(message.userId()).isEqualTo(userId),
                        () -> assertThat(message.message()).isEqualTo(messageContent),
                        () -> assertThat(message.orderSequence()).isEqualTo(1L),
                        () -> assertThat(message.createdAt()).isEqualTo(timestamp)
                ));

        SyncNewMessage syncCommand2 = reader2Probe.expectMessageClass(SyncNewMessage.class);
        assertThat(syncCommand2.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.channelId()).isEqualTo(channelId),
                        () -> assertThat(message.userId()).isEqualTo(userId),
                        () -> assertThat(message.message()).isEqualTo(messageContent),
                        () -> assertThat(message.orderSequence()).isEqualTo(1L),
                        () -> assertThat(message.createdAt()).isEqualTo(timestamp)
                ));
    }

    @Test
    void RemoveShutdownReader_메시지를_받으면_해당_reader가_ChatChannelEntity에서_제거되어_메시지를_받지_않는다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();

        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));
        channelEntity.tell(new RemoveShutdownReader("reader"));

        Long senderId = 200L;
        String messageContent = "Test message";

        channelEntity.tell(new SendMessage(senderId, messageContent));

        readerProbe.expectNoMessage(Duration.ofMillis(200));
    }

    @Test
    void SendMessage_메시지를_받으면_MessageStoragePort에_채팅_메시지_저장을_요청한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        Long userId = 100L;
        String messageContent = "Hello, World!";

        channelEntity.tell(new SendMessage(userId, messageContent));

        verify(messageStoragePort, timeout(1000)).store(any(ChatMessage.class), eq(channelEntity));
    }

    @Test
    void SendMessage_메시지를_받으면_유효한_ChatMessage를_생성하고_저장한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        Long userId = 100L;
        String messageContent = "Test message";

        channelEntity.tell(new SendMessage(userId, messageContent));

        verify(messageStoragePort, timeout(1000)).store(
                argThat(message ->
                        message.messageId() == null
                                && message.channelId().equals(channelId)
                                && message.userId().equals(userId)
                                && message.orderSequence() > 0
                                && message.message().equals(messageContent)
                ),
                eq(channelEntity)
        );
    }

    @Test
    void SyncRecentMessages_메시지를_받으면_messages를_동기화한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 10, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 10, 0, 1);
        List<ChatMessage> recentMessages = List.of(
                new ChatMessage(1L, channelId, 100L, 1L, "Message 1", timestamp1, timestamp1),
                new ChatMessage(2L, channelId, 101L, 2L, "Message 2", timestamp2, timestamp2)
        );

        channelEntity.tell(new SyncRecentMessages(recentMessages));

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RequestSyncMessages(readerProbe.ref()));

        DeliverSyncMessages delivered = readerProbe.expectMessageClass(DeliverSyncMessages.class);
        assertAll(
                () -> assertThat(delivered.messages()).hasSize(2),
                () -> assertThat(delivered.messages()).extracting(ChatMessage::message)
                                                      .containsExactly("Message 2", "Message 1")
        );
    }

    @Test
    void 메시지를_여러_번_동기화하면_messageSequence가_순차적으로_증가한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 12, 0, 1);
        LocalDateTime timestamp3 = LocalDateTime.of(2025, 10, 17, 12, 0, 2);

        ChatMessage message1 = new ChatMessage(1L, channelId, 100L, 1L, "First message", timestamp1, timestamp1);
        ChatMessage message2 = new ChatMessage(2L, channelId, 100L, 2L, "Second message", timestamp2, timestamp2);
        ChatMessage message3 = new ChatMessage(3L, channelId, 100L, 3L, "Third message", timestamp3, timestamp3);

        channelEntity.tell(new SyncPersistedMessage(message1));
        channelEntity.tell(new SyncPersistedMessage(message2));
        channelEntity.tell(new SyncPersistedMessage(message3));

        SyncNewMessage sync1 = readerProbe.expectMessageClass(SyncNewMessage.class);
        assertThat(sync1.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.orderSequence()).isEqualTo(1L),
                        () -> assertThat(message.message()).isEqualTo("First message"),
                        () -> assertThat(message.createdAt()).isEqualTo(timestamp1)
                ));

        SyncNewMessage sync2 = readerProbe.expectMessageClass(SyncNewMessage.class);
        assertThat(sync2.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.orderSequence()).isEqualTo(2L),
                        () -> assertThat(message.message()).isEqualTo("Second message"),
                        () -> assertThat(message.createdAt()).isEqualTo(timestamp2)
                ));

        SyncNewMessage sync3 = readerProbe.expectMessageClass(SyncNewMessage.class);
        assertThat(sync3.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.orderSequence()).isEqualTo(3L),
                        () -> assertThat(message.message()).isEqualTo("Third message"),
                        () -> assertThat(message.createdAt()).isEqualTo(timestamp3)
                ));
    }

    @Test
    void 생성_시_findRecentMessages를_호출한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        testKit.spawn(ChannelEntity.create(
                Clock.systemDefaultZone(),
                channelId,
                messages,
                messageStoragePort,
                channelActorStoragePort,
                membershipStoragePort
        ));

        verify(messageStoragePort, timeout(1000)).findRecentMessages(eq(channelId), eq(50), any());
    }

    @Test
    void RequestSyncMessages_메시지를_받으면_요청한_reader에게_DeliverSyncMessages를_전달한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 12, 0, 1);
        ChatMessage message1 = new ChatMessage(1L, channelId, 100L, 1L, "Message 1", timestamp1, timestamp1);
        ChatMessage message2 = new ChatMessage(2L, channelId, 100L, 2L, "Message 2", timestamp2, timestamp2);

        channelEntity.tell(new SyncPersistedMessage(message1));
        channelEntity.tell(new SyncPersistedMessage(message2));

        channelEntity.tell(new RequestSyncMessages(readerProbe.ref()));

        DeliverSyncMessages delivered = readerProbe.expectMessageClass(DeliverSyncMessages.class);
        assertAll(
                () -> assertThat(delivered.messages()).hasSize(2),
                () -> assertThat(delivered.messages()).extracting(ChatMessage::message)
                                                      .containsExactly("Message 2", "Message 1")
        );
    }

    @Test
    void DeleteMessage_메시지를_받으면_MessageStoragePort에_메시지_삭제를_요청한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        Long messageId = 1L;

        channelEntity.tell(new DeleteMessage(messageId));

        verify(messageStoragePort, timeout(1000)).delete(eq(messageId), eq(channelEntity));
    }

    @Test
    void SyncDeletedMessage_메시지를_받으면_모든_reader에게_SyncDeletion_메시지를_전달한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        TestProbe<ChannelReaderCommand> reader1Probe = testKit.createTestProbe();
        TestProbe<ChannelReaderCommand> reader2Probe = testKit.createTestProbe();

        channelEntity.tell(new RegisterReader("reader1", reader1Probe.ref()));
        channelEntity.tell(new RegisterReader("reader2", reader2Probe.ref()));

        LocalDateTime timestamp = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        ChatMessage message = new ChatMessage(1L, channelId, 100L, 1L, "Test", timestamp, timestamp);
        channelEntity.tell(new SyncPersistedMessage(message));

        reader1Probe.expectMessageClass(SyncNewMessage.class);
        reader2Probe.expectMessageClass(SyncNewMessage.class);

        Long messageId = 1L;

        channelEntity.tell(new SyncDeletedMessage(messageId));

        SyncDeletion deletion1 = reader1Probe.expectMessageClass(SyncDeletion.class);
        assertThat(deletion1.messageId()).isEqualTo(messageId);

        SyncDeletion deletion2 = reader2Probe.expectMessageClass(SyncDeletion.class);
        assertThat(deletion2.messageId()).isEqualTo(messageId);
    }

    @Test
    void SyncDeletedMessage_메시지를_받으면_messages에서_메시지가_삭제된다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 12, 0, 1);
        ChatMessage message1 = new ChatMessage(1L, channelId, 100L, 1L, "Message 1", timestamp1, timestamp1);
        ChatMessage message2 = new ChatMessage(2L, channelId, 100L, 2L, "Message 2", timestamp2, timestamp2);

        channelEntity.tell(new SyncPersistedMessage(message1));
        readerProbe.expectMessageClass(SyncNewMessage.class);

        channelEntity.tell(new SyncPersistedMessage(message2));
        readerProbe.expectMessageClass(SyncNewMessage.class);

        channelEntity.tell(new SyncDeletedMessage(1L));
        readerProbe.expectMessageClass(SyncDeletion.class);

        TestProbe<ChannelReaderCommand> syncProbe = testKit.createTestProbe();
        channelEntity.tell(new RequestSyncMessages(syncProbe.ref()));

        DeliverSyncMessages delivered = syncProbe.expectMessageClass(DeliverSyncMessages.class);
        assertAll(
                () -> assertThat(delivered.messages()).hasSize(1),
                () -> assertThat(delivered.messages()).extracting(ChatMessage::messageId)
                                                      .containsExactly(2L)
        );
    }

    @Test
    void UpdateMessage_메시지를_받으면_MessageStoragePort에_메시지_수정을_요청한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        Long messageId = 1L;
        String updatedMessage = "Updated message";

        channelEntity.tell(new UpdateMessage(messageId, updatedMessage));

        verify(messageStoragePort, timeout(1000)).update(eq(messageId), eq(updatedMessage), eq(channelEntity));
    }

    @Test
    void SyncUpdatedMessage_메시지를_받으면_messages에서_메시지를_수정하고_reader에게_전파한다() {
        Long channelId = 1L;
        ChannelEntityChatMessages messages = new ChannelEntityChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mockChannelActorStoragePort(channelId);
        ChannelMembershipActorStoragePort membershipStoragePort = mockMembershipStoragePort();

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());

        ActorRef<ChannelEntityCommand> channelEntity =
                testKit.spawn(ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        messageStoragePort,
                        channelActorStoragePort,
                        membershipStoragePort
                ));

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 12, 0, 1);
        ChatMessage message1 = new ChatMessage(1L, channelId, 100L, 1L, "Message 1", timestamp1, timestamp1);
        ChatMessage message2 = new ChatMessage(2L, channelId, 100L, 2L, "Message 2", timestamp2, timestamp2);

        channelEntity.tell(new SyncPersistedMessage(message1));
        readerProbe.expectMessageClass(SyncNewMessage.class);

        channelEntity.tell(new SyncPersistedMessage(message2));
        readerProbe.expectMessageClass(SyncNewMessage.class);

        channelEntity.tell(new SyncUpdatedMessage(1L, "Updated Message 1"));

        SyncUpdate syncUpdate = readerProbe.expectMessageClass(SyncUpdate.class);
        assertAll(
                () -> assertThat(syncUpdate.messageId()).isEqualTo(1L),
                () -> assertThat(syncUpdate.updatedMessage()).isEqualTo("Updated Message 1"),
                () -> assertThat(syncUpdate.updatedAt()).isNotNull()
        );

        TestProbe<ChannelReaderCommand> syncProbe = testKit.createTestProbe();
        channelEntity.tell(new RequestSyncMessages(syncProbe.ref()));

        DeliverSyncMessages delivered = syncProbe.expectMessageClass(DeliverSyncMessages.class);
        assertAll(
                () -> assertThat(delivered.messages()).hasSize(2),
                () -> assertThat(delivered.messages())
                        .filteredOn(msg -> msg.messageId().equals(1L))
                        .extracting(ChatMessage::message)
                        .containsExactly("Updated Message 1"),
                () -> assertThat(delivered.messages())
                        .filteredOn(msg -> msg.messageId().equals(2L))
                        .extracting(ChatMessage::message)
                        .containsExactly("Message 2")
        );
    }

    private ChannelActorStoragePort mockChannelActorStoragePort(Long channelId) {
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        Channel channel = Channel.create(
                channelId,
                "channel-" + channelId,
                channelId,
                ChannelPolicy.defaultPolicy(),
                new HashMap<>(),
                LocalDateTime.now()
        ).withAssignedId(channelId);

        doAnswer(invocation -> {
            ActorRef<ChannelEntityCommand> replyTo = invocation.getArgument(1);
            replyTo.tell(new ChannelLoaded(channel));
            return null;
        }).when(channelActorStoragePort).find(anyLong(), any());

        doAnswer(invocation -> {
            ActorRef<ChannelEntityCommand> replyTo = invocation.getArgument(1);
            Long batchId = invocation.getArgument(2);
            List<ChannelDomainEvent> batch = invocation.getArgument(3);
            replyTo.tell(new ChannelBatchPersisted(batchId, batch, true, ""));
            return null;
        }).when(channelActorStoragePort).update(any(), any(), anyLong(), any());

        return channelActorStoragePort;
    }

    private ChannelMembershipActorStoragePort mockMembershipStoragePort() {
        ChannelMembershipActorStoragePort membershipStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(membershipStoragePort).joinChannel(any(), any());
        doNothing().when(membershipStoragePort).leaveChannel(any(ChannelMembership.class));
        doNothing().when(membershipStoragePort).leaveChannel(any(ChannelId.class), any(UserId.class));
        doNothing().when(membershipStoragePort).promoteToManager(any());
        doNothing().when(membershipStoragePort).demoteToMember(any());
        doNothing().when(membershipStoragePort).addPermission(any(), any());
        doNothing().when(membershipStoragePort).removePermission(any(), any());
        doNothing().when(membershipStoragePort).kickMember(any(ChannelMembership.class));
        doNothing().when(membershipStoragePort).kickMember(any(ChannelId.class), any(UserId.class));

        return membershipStoragePort;
    }
}
