package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChannelEntity.DomainEventProcessed;
import com.tok.pekko.domain.chat.actor.ChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.DeliverSyncMessages;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyChangeChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyEditChannelName;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChangeChannelPolicy;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.DeleteMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.EditChannelName;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.InviteUser;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.JoinUser;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveChannelMetadata;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveMembership;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SendMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncChannel;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncDeletedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncRecentMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncStoredMembership;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncUpdatedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.UpdateMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.NotifyMembershipCountChanged;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncChannelMetadata;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncMembership;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import com.tok.pekko.domain.chat.port.out.InviteUserEventProtocol.InviteUserEventCommand;
import com.tok.pekko.domain.chat.port.out.InviteUserEventProtocol.Invited;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.internal.pubsub.TopicImpl;
import org.apache.pekko.actor.typed.pubsub.Topic;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"NonAsciiCharacters", "unchecked"})
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
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();

        // when
        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        // then
        readerProbe.expectNoMessage(Duration.ofMillis(200));
    }

    @Test
    void SyncPersistedMessage_메시지를_받으면_모든_reader에게_SyncNewMessage_메시지를_전달한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

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

        // when
        channelEntity.tell(new SyncPersistedMessage(persistedMessage));

        // then
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
    void RemoveShutdownReader_메시지를_받으면_해당_reader가_ChannelEntity에서_제거되어_메시지를_받지_않는다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();

        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        // when
        channelEntity.tell(new RemoveShutdownReader("reader"));

        Long senderId = 200L;
        String messageContent = "Test message";

        channelEntity.tell(new SendMessage(senderId, messageContent));

        // then
        readerProbe.expectNoMessage(Duration.ofMillis(200));
    }

    @Test
    void SendMessage_메시지를_받으면_MessageStoragePort에_채팅_메시지_저장을_요청한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        Long userId = 100L;
        String messageContent = "Test message";

        // when
        channelEntity.tell(new SendMessage(userId, messageContent));

        // then
        verify(messageStoragePort, timeout(1000)).store(any(ChatMessage.class), eq(channelEntity));
    }

    @Test
    void SendMessage_메시지를_받으면_유효한_ChatMessage를_생성하고_저장한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        Long userId = 100L;
        String messageContent = "Test message";

        // when
        channelEntity.tell(new SendMessage(userId, messageContent));

        // then
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
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 10, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 10, 0, 1);
        List<ChatMessage> recentMessages = List.of(
                new ChatMessage(1L, channelId, 100L, 1L, "Message 1", timestamp1, timestamp1),
                new ChatMessage(2L, channelId, 101L, 2L, "Message 2", timestamp2, timestamp2)
        );

        // when
        channelEntity.tell(new SyncRecentMessages(recentMessages));

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RequestSyncMessages(readerProbe.ref()));

        // then
        DeliverSyncMessages actual = readerProbe.expectMessageClass(DeliverSyncMessages.class);

        assertAll(
                () -> assertThat(actual.messages()).hasSize(2),
                () -> assertThat(actual.messages()).extracting(ChatMessage::message)
                                                      .containsExactly("Message 2", "Message 1")
        );
    }

    @Test
    void 메시지를_여러_번_동기화하면_messageSequence가_순차적으로_증가한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 12, 0, 1);
        LocalDateTime timestamp3 = LocalDateTime.of(2025, 10, 17, 12, 0, 2);

        ChatMessage message1 = new ChatMessage(1L, channelId, 100L, 1L, "First message", timestamp1, timestamp1);
        ChatMessage message2 = new ChatMessage(2L, channelId, 100L, 2L, "Second message", timestamp2, timestamp2);
        ChatMessage message3 = new ChatMessage(3L, channelId, 100L, 3L, "Third message", timestamp3, timestamp3);

        // when
        channelEntity.tell(new SyncPersistedMessage(message1));
        channelEntity.tell(new SyncPersistedMessage(message2));
        channelEntity.tell(new SyncPersistedMessage(message3));

        // then
        SyncNewMessage syncNewMessage1 = readerProbe.expectMessageClass(SyncNewMessage.class);

        assertThat(syncNewMessage1.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.orderSequence()).isEqualTo(1L),
                        () -> assertThat(message.message()).isEqualTo("First message"),
                        () -> assertThat(message.createdAt()).isEqualTo(timestamp1)
                ));

        SyncNewMessage syncNewMessage2 = readerProbe.expectMessageClass(SyncNewMessage.class);

        assertThat(syncNewMessage2.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.orderSequence()).isEqualTo(2L),
                        () -> assertThat(message.message()).isEqualTo("Second message"),
                        () -> assertThat(message.createdAt()).isEqualTo(timestamp2)
                ));

        SyncNewMessage syncNewMessage3 = readerProbe.expectMessageClass(SyncNewMessage.class);

        assertThat(syncNewMessage3.message())
                .isNotNull()
                .satisfies(message -> assertAll(
                        () -> assertThat(message.orderSequence()).isEqualTo(3L),
                        () -> assertThat(message.message()).isEqualTo("Third message"),
                        () -> assertThat(message.createdAt()).isEqualTo(timestamp3)
                ));
    }

    @Test
    void 생성_시_findRecentMessages와_find를_호출한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        // when
        testKit.spawn(ChannelEntity.create(
                Clock.systemDefaultZone(),
                channelId,
                messages,
                clusterSharding,
                messageStoragePort,
                channelActorStoragePort,
                mock(ActorRef.class)
        ));

        // then
        verify(messageStoragePort, timeout(1000)).findRecentMessages(eq(channelId), eq(50), any());
        verify(channelActorStoragePort, timeout(1000)).find(eq(channelId), any());
    }

    @Test
    void RequestSyncMessages_메시지를_받으면_요청한_reader에게_DeliverSyncMessages를_전달한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 12, 0, 1);
        ChatMessage message1 = new ChatMessage(1L, channelId, 100L, 1L, "Message 1", timestamp1, timestamp1);
        ChatMessage message2 = new ChatMessage(2L, channelId, 100L, 2L, "Message 2", timestamp2, timestamp2);

        List<ChatMessage> recentMessages = List.of(message1, message2);
        channelEntity.tell(new SyncRecentMessages(recentMessages));

        // when
        channelEntity.tell(new RequestSyncMessages(readerProbe.ref()));

        // then
        DeliverSyncMessages actual = readerProbe.expectMessageClass(DeliverSyncMessages.class);

        assertAll(
                () -> assertThat(actual.messages()).hasSize(2),
                () -> assertThat(actual.messages()).extracting(ChatMessage::message)
                                                      .containsExactly("Message 2", "Message 1")
        );
    }

    @Test
    void DeleteMessage_메시지를_받으면_MessageStoragePort에_메시지_삭제를_요청한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        Long messageId = 1L;

        // when
        channelEntity.tell(new DeleteMessage(messageId));

        // then
        verify(messageStoragePort, timeout(1000)).delete(eq(messageId), eq(channelEntity));
    }

    @Test
    void SyncDeletedMessage_메시지를_받으면_모든_reader에게_SyncDeletion_메시지를_전달한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

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

        // when
        channelEntity.tell(new SyncDeletedMessage(messageId));

        // then
        SyncDeletion syncDeletion1 = reader1Probe.expectMessageClass(SyncDeletion.class);

        assertThat(syncDeletion1.messageId()).isEqualTo(messageId);

        SyncDeletion syncDeletion2 = reader2Probe.expectMessageClass(SyncDeletion.class);

        assertThat(syncDeletion2.messageId()).isEqualTo(messageId);
    }

    @Test
    void SyncDeletedMessage_메시지를_받으면_messages에서_메시지가_삭제된다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        channelEntity.tell(new SyncRecentMessages(List.of()));

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

        // when
        channelEntity.tell(new SyncDeletedMessage(1L));
        readerProbe.expectMessageClass(SyncDeletion.class);

        TestProbe<ChannelReaderCommand> syncProbe = testKit.createTestProbe();
        channelEntity.tell(new RequestSyncMessages(syncProbe.ref()));

        // then
        DeliverSyncMessages actual = syncProbe.expectMessageClass(DeliverSyncMessages.class);

        assertAll(
                () -> assertThat(actual.messages()).hasSize(1),
                () -> assertThat(actual.messages()).extracting(ChatMessage::messageId)
                                                      .containsExactly(2L)
        );
    }

    @Test
    void UpdateMessage_메시지를_받으면_MessageStoragePort에_메시지_수정을_요청한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        Long messageId = 1L;
        String updatedMessage = "Updated message";

        // when
        channelEntity.tell(new UpdateMessage(messageId, updatedMessage));

        // then
        verify(messageStoragePort, timeout(1000)).update(eq(messageId), eq(updatedMessage), eq(channelEntity));
    }

    @Test
    void SyncUpdatedMessage_메시지를_받으면_messages에서_메시지를_수정하고_reader에게_전파한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        channelEntity.tell(new SyncRecentMessages(List.of()));

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

        // when
        channelEntity.tell(new SyncUpdatedMessage(1L, "Updated Message 1"));

        // then
        SyncUpdate syncUpdate = readerProbe.expectMessageClass(SyncUpdate.class);

        assertAll(
                () -> assertThat(syncUpdate.messageId()).isEqualTo(1L),
                () -> assertThat(syncUpdate.updatedMessage()).isEqualTo("Updated Message 1"),
                () -> assertThat(syncUpdate.updatedAt()).isNotNull()
        );

        TestProbe<ChannelReaderCommand> syncProbe = testKit.createTestProbe();

        channelEntity.tell(new RequestSyncMessages(syncProbe.ref()));

        DeliverSyncMessages deliverSyncMessages = syncProbe.expectMessageClass(DeliverSyncMessages.class);

        assertAll(
                () -> assertThat(deliverSyncMessages.messages()).hasSize(2),
                () -> assertThat(deliverSyncMessages.messages())
                        .filteredOn(msg -> msg.messageId().equals(1L))
                        .extracting(ChatMessage::message)
                        .containsExactly("Updated Message 1"),
                () -> assertThat(deliverSyncMessages.messages())
                        .filteredOn(msg -> msg.messageId().equals(2L))
                        .extracting(ChatMessage::message)
                        .containsExactly("Message 2")
        );
    }

    @Test
    void InviteUser_메시지를_받으면_Topic에_Invited_이벤트를_발행한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);
        ActorRef<Topic.Command<InviteUserEventCommand>> inviteUserTopic = mock(ActorRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        inviteUserTopic
                )
        );

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        UserId ownerId = UserId.create(1L);
        LocalDateTime now = LocalDateTime.now();

        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        ChannelMembership ownerMembership = ChannelMembership.create(
                1L,
                channelId,
                ownerId.getValue(),
                ChannelRole.OWNER,
                ChannelManagePermissions.ofOwner(),
                now
        );
        memberships.put(ownerId, ownerMembership);

        Channel channel = Channel.create(
                channelId,
                "test-channel",
                ownerId.getValue(),
                ChannelPolicy.defaultPolicy(),
                memberships,
                now
        );

        channelEntity.tell(new SyncChannel(channel));

        readerProbe.expectNoMessage(Duration.ofMillis(200));

        UserId inviterId = UserId.create(1L);
        UserId inviteeId = UserId.create(2L);
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe();

        // when
        channelEntity.tell(new InviteUser(inviterId, inviteeId, replyProbe.ref()));

        readerProbe.expectMessageClass(NotifyMembershipCountChanged.class);

        // then
        verify(inviteUserTopic, timeout(1000)).tell(
                argThat(command ->
                        command instanceof TopicImpl.Publish &&
                                ((TopicImpl.Publish<?>) command).message() instanceof Invited &&
                                ((Invited) ((TopicImpl.Publish<?>) command).message()).channelId().equals(channelId) &&
                                ((Invited) ((TopicImpl.Publish<?>) command).message()).inviteeId().equals(inviteeId.getValue())
                )
        );
    }

    @Test
    void JoinUser_메시지를_받으면_ChannelEventHandler에_HandleUserJoined를_전송한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        Channel channel = Channel.create("test-channel", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        channelEntity.tell(new SyncChannel(channel));

        UserId joinerId = UserId.create(2L);
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe();

        // when
        channelEntity.tell(new JoinUser(joinerId, replyProbe.ref()));

        // then
        verify(entityRef, timeout(1000)).tell(any());
    }

    @Test
    void ChangeChannelPolicy_메시지를_받으면_reader에게_NotifyChangeChannelPolicy를_전파한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        UserId ownerId = UserId.create(1L);
        LocalDateTime now = LocalDateTime.now();

        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        ChannelMembership ownerMembership = ChannelMembership.create(
                1L,
                channelId,
                ownerId.getValue(),
                ChannelRole.OWNER,
                ChannelManagePermissions.ofOwner(),
                now
        );
        memberships.put(ownerId, ownerMembership);

        Channel channel = Channel.create(
                channelId,
                "test-channel",
                ownerId.getValue(),
                ChannelPolicy.defaultPolicy(),
                memberships,
                now
        );

        channelEntity.tell(new SyncChannel(channel));

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        UserId changerId = UserId.create(1L);

        // when
        channelEntity.tell(new ChangeChannelPolicy(changerId, true, true, false));

        // then
        NotifyChangeChannelPolicy actual = readerProbe.expectMessageClass(NotifyChangeChannelPolicy.class);

        assertThat(actual.channelPolicy()).isNotNull();
    }

    @Test
    void EditChannelName_메시지를_받으면_reader에게_NotifyEditChannelName을_전파한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        UserId ownerId = UserId.create(1L);
        LocalDateTime now = LocalDateTime.now();

        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        ChannelMembership ownerMembership = ChannelMembership.create(
                1L,
                channelId,
                ownerId.getValue(),
                ChannelRole.OWNER,
                ChannelManagePermissions.ofOwner(),
                now
        );
        memberships.put(ownerId, ownerMembership);

        Channel channel = Channel.create(
                channelId,
                "test-channel",
                ownerId.getValue(),
                ChannelPolicy.defaultPolicy(),
                memberships,
                now
        );

        channelEntity.tell(new SyncChannel(channel));

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        UserId changerId = UserId.create(1L);
        String newName = "new-channel-name";

        // when
        channelEntity.tell(new EditChannelName(changerId, newName));

        // then
        NotifyEditChannelName actual = readerProbe.expectMessageClass(NotifyEditChannelName.class);

        assertThat(actual.editedName()).isEqualTo(newName);
    }

    @Test
    void SyncChannel_메시지를_받으면_pendingChannelCommands를_처리한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RegisterReader("reader", readerProbe.ref()));

        UserId changerId = UserId.create(1L);
        String newName = "new-name";

        channelEntity.tell(new EditChannelName(changerId, newName));

        UserId ownerId = UserId.create(1L);
        LocalDateTime now = LocalDateTime.now();

        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        ChannelMembership ownerMembership = ChannelMembership.create(
                1L,
                channelId,
                ownerId.getValue(),
                ChannelRole.OWNER,
                ChannelManagePermissions.ofOwner(),
                now
        );
        memberships.put(ownerId, ownerMembership);

        Channel channel = Channel.create(
                channelId,
                "test-channel",
                ownerId.getValue(),
                ChannelPolicy.defaultPolicy(),
                memberships,
                now
        );

        // when
        channelEntity.tell(new SyncChannel(channel));

        // then
        NotifyEditChannelName actual = readerProbe.expectMessageClass(NotifyEditChannelName.class);

        assertThat(actual.editedName()).isEqualTo(newName);
    }

    @Test
    void RequestSyncMessages_메시지를_받으면_initialMessagesLoaded가_false면_pendingInitialSyncReaders에_추가한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();

        // when
        channelEntity.tell(new RequestSyncMessages(readerProbe.ref()));

        // then
        readerProbe.expectNoMessage(Duration.ofMillis(200));
    }

    @Test
    void SyncRecentMessages_메시지를_받으면_pendingInitialSyncReaders에게_DeliverSyncMessages를_전송한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        channelEntity.tell(new RequestSyncMessages(readerProbe.ref()));

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 12, 0, 0);
        ChatMessage message1 = new ChatMessage(1L, channelId, 100L, 1L, "Message 1", timestamp1, timestamp1);
        List<ChatMessage> recentMessages = List.of(message1);

        // when
        channelEntity.tell(new SyncRecentMessages(recentMessages));

        // then
        DeliverSyncMessages actual = readerProbe.expectMessageClass(DeliverSyncMessages.class);

        assertThat(actual.messages()).hasSize(1);
    }

    @Test
    void DomainEventProcessed_메시지를_받으면_events에서_해당_이벤트를_제거한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        Channel channel = Channel.create("test-channel", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        channelEntity.tell(new SyncChannel(channel));

        UserId changerId = UserId.create(1L);
        channelEntity.tell(new EditChannelName(changerId, "new-name"));

        // when
        channelEntity.tell(new DomainEventProcessed(1L));

        // then
        testKit.createTestProbe().expectNoMessage(Duration.ofMillis(200));
    }

    @Test
    void ResolveChannelMetadata_메시지를_받으면_SyncChannelMetadata를_응답한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        String channelName = "test-channel";
        UserId ownerId = UserId.create(1L);
        LocalDateTime now = LocalDateTime.now();

        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        ChannelMembership ownerMembership = ChannelMembership.create(
                1L,
                channelId,
                ownerId.getValue(),
                ChannelRole.OWNER,
                ChannelManagePermissions.ofOwner(),
                now
        );
        memberships.put(ownerId, ownerMembership);

        Channel channel = Channel.create(
                channelId,
                channelName,
                ownerId.getValue(),
                ChannelPolicy.defaultPolicy(),
                memberships,
                now
        );

        channelEntity.tell(new SyncChannel(channel));

        TestProbe<ChannelReaderCommand> replyProbe = testKit.createTestProbe();

        // when
        channelEntity.tell(new ResolveChannelMetadata(replyProbe.ref()));

        // then
        SyncChannelMetadata actual = replyProbe.expectMessageClass(SyncChannelMetadata.class);

        assertAll(
                () -> assertThat(actual.channelId()).isEqualTo(channelId),
                () -> assertThat(actual.name()).isEqualTo(channelName),
                () -> assertThat(actual.channelPolicy()).isNotNull()
        );
    }

    @Test
    void ResolveMembership_메시지를_받으면_SyncMembership을_응답한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        Channel channel = Channel.create("test-channel", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        channelEntity.tell(new SyncChannel(channel));

        UserId userId = UserId.create(2L);
        TestProbe<ChannelEntityCommand> joinReplyProbe = testKit.createTestProbe();
        channelEntity.tell(new JoinUser(userId, joinReplyProbe.ref()));

        TestProbe<ChannelReaderCommand> replyProbe = testKit.createTestProbe();

        // when
        channelEntity.tell(new ResolveMembership(userId, replyProbe.ref()));

        // then
        SyncMembership actual = replyProbe.expectMessageClass(SyncMembership.class);

        assertAll(
                () -> assertThat(actual.userId()).isEqualTo(userId.getValue()),
                () -> assertThat(actual.membership()).isNotNull()
        );
    }

    @Test
    void SyncStoredMembership_메시지를_받으면_Channel의_membership을_업데이트한다() {
        // given
        Long channelId = 1L;
        ChatMessages messages = new ChatMessages();
        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEventHandlerCommand> entityRef = mock(EntityRef.class);

        doNothing().when(messageStoragePort).findRecentMessages(anyLong(), anyInt(), any());
        doNothing().when(channelActorStoragePort).find(anyLong(), any());
        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(entityRef);

        ActorRef<ChannelEntityCommand> channelEntity = testKit.spawn(
                ChannelEntity.create(
                        Clock.systemDefaultZone(),
                        channelId,
                        messages,
                        clusterSharding,
                        messageStoragePort,
                        channelActorStoragePort,
                        mock(ActorRef.class)
                )
        );

        Channel channel = Channel.create("test-channel", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        channelEntity.tell(new SyncChannel(channel));

        ChannelMembership membership = ChannelMembership.create(
                1L,
                channelId,
                2L,
                ChannelRole.MEMBER,
                ChannelManagePermissions.ofMember(),
                LocalDateTime.now()
        );

        // when
        channelEntity.tell(new SyncStoredMembership(membership));

        // then
        testKit.createTestProbe().expectNoMessage(Duration.ofMillis(200));
    }
}
