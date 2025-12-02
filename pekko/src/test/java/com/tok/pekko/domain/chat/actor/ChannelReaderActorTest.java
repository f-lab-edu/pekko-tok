package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.GetHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.NotifyFailure;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.NotifyMembershipCountChanged;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RequestInitialHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncChannelMetadata;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncMembership;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateChangeChannelMembership;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateChangeChannelPolicy;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateEditChannelName;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateFailure;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateKickedMember;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateMembershipCount;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncLeaveChannel;
import java.util.EnumSet;
import java.util.Set;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SuppressWarnings({"NonAsciiCharacters", "unchecked"})
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelReaderActorTest {

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
    void SyncNewMessage_메시지를_받으면_채팅_메시지를_ChatMessages에_추가하고_모든_ClientSessionActor에_전달한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage newMessage = ChatMessage.create(
                1L,
                1001L,
                1L,
                "Hello World",
                timestamp,
                timestamp
        );

        readerActor.tell(new RegisterClientSession(100L, clientSessionProbe1.ref()));
        readerActor.tell(new RegisterClientSession(101L, clientSessionProbe2.ref()));

        completeInitialSync(readerActor);

        // when
        readerActor.tell(new SyncNewMessage(newMessage));

        // then
        verify(mockMessages, timeout(1000)).add(newMessage);

        DeliverNewMessage deliveredMessage1 = clientSessionProbe1.expectMessageClass(
                DeliverNewMessage.class,
                Duration.ofSeconds(3)
        );
        DeliverNewMessage deliveredMessage2 = clientSessionProbe2.expectMessageClass(
                DeliverNewMessage.class,
                Duration.ofSeconds(3)
        );

        assertAll(
                () -> assertThat(deliveredMessage1.message()).isEqualTo(newMessage),
                () -> assertThat(deliveredMessage2.message()).isEqualTo(newMessage)
        );
    }

    @Test
    void GetHistory_메시지를_받았을_때_히스토리가_존재하면_ClientSession에_DeliverHistory를_전달한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> replyToProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        long messageSequence = 100L;
        int size = 10;
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = LocalDateTime.now();
        LocalDateTime timestamp3 = LocalDateTime.now();
        List<ChatMessage> historyMessages = List.of(
                new ChatMessage(1L, 90L, 2001L, 90L, "Message 90", timestamp1, timestamp1),
                new ChatMessage(1L, 91L, 2002L, 91L, "Message 91", timestamp2, timestamp2),
                new ChatMessage(1L, 92L, 2001L, 92L, "Message 92", timestamp3, timestamp3)
        );

        given(mockMessages.getHistory(messageSequence, size)).willReturn(historyMessages);

        // when
        readerActor.tell(new GetHistory(messageSequence, size, replyToProbe.ref()));

        // then
        verify(mockMessages, timeout(1000)).getHistory(messageSequence, size);

        DeliverHistory deliveredHistory = replyToProbe.expectMessageClass(
                DeliverHistory.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(deliveredHistory.history()).hasSize(3),
                () -> assertThat(deliveredHistory.history()).isEqualTo(historyMessages),
                () -> assertThat(deliveredHistory.channelId()).isEqualTo(1L),
                () -> assertThat(deliveredHistory.messageSequence()).isEqualTo(messageSequence),
                () -> assertThat(deliveredHistory.size()).isEqualTo(size)
        );
    }

    @Test
    void GetHistory_메시지를_받았을_때_히스토리가_비어있으면_ChannelEntity에_ResolveHistory를_전달한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> replyToProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        long messageSequence = 10L;
        int size = 5;
        given(mockMessages.getHistory(messageSequence, size)).willReturn(List.of());

        // when
        readerActor.tell(new GetHistory(messageSequence, size, replyToProbe.ref()));

        // then
        verify(mockMessages, timeout(1000)).getHistory(messageSequence, size);

        ArgumentCaptor<ChannelEntityCommand> captor = ArgumentCaptor.forClass(ChannelEntityCommand.class);
        verify(channelEntity, timeout(1000).times(2)).tell(captor.capture());

        List<ChannelEntityCommand> allMessages = captor.getAllValues();

        assertAll(
                () -> assertThat(allMessages.get(0)).isInstanceOf(RequestSyncMessages.class),
                () -> assertThat(allMessages.get(1)).isInstanceOf(ResolveHistory.class),
                () -> {
                    ResolveHistory resolveHistory = (ResolveHistory) allMessages.get(1);
                    assertThat(resolveHistory.messageSequence()).isEqualTo(messageSequence);
                    assertThat(resolveHistory.size()).isEqualTo(size);
                }
        );
    }

    @Test
    void SyncDeletion_메시지를_받으면_ChatMessages에서_메시지를_삭제하고_모든_ClientSessionActor에_전달한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        Long messageId = 1L;

        readerActor.tell(new RegisterClientSession(100L, clientSessionProbe1.ref()));
        readerActor.tell(new RegisterClientSession(101L, clientSessionProbe2.ref()));

        completeInitialSync(readerActor);

        // when
        readerActor.tell(new SyncDeletion(messageId));

        // then
        verify(mockMessages, timeout(1000)).delete(messageId);

        DeliverDeletedMessage deliveredDeletedMessage1 = clientSessionProbe1.expectMessageClass(
                DeliverDeletedMessage.class,
                Duration.ofSeconds(3)
        );
        DeliverDeletedMessage deliveredDeletedMessage2 = clientSessionProbe2.expectMessageClass(
                DeliverDeletedMessage.class,
                Duration.ofSeconds(3)
        );

        assertAll(
                () -> assertThat(deliveredDeletedMessage1.deletedMessageId()).isEqualTo(messageId),
                () -> assertThat(deliveredDeletedMessage2.deletedMessageId()).isEqualTo(messageId)
        );
    }

    @Test
    void SyncUpdate_메시지를_받으면_ChatMessages에서_메시지를_수정하고_모든_ClientSessionActor에_전달한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        Long messageId = 1L;
        String updatedMessageContent = "Updated Message";
        LocalDateTime timestamp = LocalDateTime.now();

        readerActor.tell(new RegisterClientSession(100L, clientSessionProbe1.ref()));
        readerActor.tell(new RegisterClientSession(101L, clientSessionProbe2.ref()));
        completeInitialSync(readerActor);

        // when
        readerActor.tell(new SyncUpdate(messageId, updatedMessageContent, timestamp));

        // then
        verify(mockMessages, timeout(1000)).update(eq(messageId), eq(updatedMessageContent), any(LocalDateTime.class));

        DeliverUpdatedMessage deliveredUpdatedMessage1 = clientSessionProbe1.expectMessageClass(
                DeliverUpdatedMessage.class,
                Duration.ofSeconds(3)
        );
        DeliverUpdatedMessage deliveredUpdatedMessage2 = clientSessionProbe2.expectMessageClass(
                DeliverUpdatedMessage.class,
                Duration.ofSeconds(3)
        );

        assertAll(
                () -> assertThat(deliveredUpdatedMessage1.messageId()).isEqualTo(messageId),
                () -> assertThat(deliveredUpdatedMessage1.updatedMessage()).isEqualTo(updatedMessageContent),
                () -> assertThat(deliveredUpdatedMessage1.updatedAt()).isEqualTo(timestamp),
                () -> assertThat(deliveredUpdatedMessage2.messageId()).isEqualTo(messageId),
                () -> assertThat(deliveredUpdatedMessage2.updatedMessage()).isEqualTo(updatedMessageContent),
                () -> assertThat(deliveredUpdatedMessage2.updatedAt()).isEqualTo(timestamp)
        );
    }

    @Test
    void RegisterClientSession_메시지를_받으면_clientSessions에_등록된다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> registeredSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        Long userId = 100L;

        // when
        readerActor.tell(new RegisterClientSession(userId, registeredSessionProbe.ref()));

        completeInitialSync(readerActor);

        // then
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage newMessage = ChatMessage.create(
                1L,
                1001L,
                1L,
                "Test",
                timestamp,
                timestamp
        );

        readerActor.tell(new SyncNewMessage(newMessage));

        DeliverNewMessage deliveredMessage = registeredSessionProbe.expectMessageClass(
                DeliverNewMessage.class,
                Duration.ofSeconds(3)
        );
        assertThat(deliveredMessage.message()).isEqualTo(newMessage);
    }

    @Test
    void RequestInitialHistory_메시지를_받으면_채팅_히스토리를_ClientSessionActor에게_전달한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> mockChannelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe();

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, mockChannelEntity, registryProbe.ref())
        );

        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe();

        List<ChatMessage> historyMessages = List.of(
                mock(ChatMessage.class),
                mock(ChatMessage.class)
        );

        given(mockMessages.getMessages()).willReturn(historyMessages);

        completeInitialSync(readerActor);

        // when
        readerActor.tell(new RequestInitialHistory(replyProbe.ref()));

        // then
        FoundHistory foundHistory = replyProbe.expectMessageClass(FoundHistory.class);
        assertThat(foundHistory.history()).isEqualTo(historyMessages);
    }

    @Test
    void RequestInitialHistory_메시지를_받았을_때_채팅_히스토리가_동기화되지_않았다면_별도로_관리된다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> mockChannelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe();

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, mockChannelEntity, registryProbe.ref())
        );

        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe();

        given(mockMessages.getMessages()).willReturn(List.of());

        // when
        readerActor.tell(new RequestInitialHistory(replyProbe.ref()));

        // then
        replyProbe.expectNoMessage(Duration.ofMillis(100));
    }

    @Test
    void DeliverSyncMessages_이전까지_대기중이던_RequestInitialHistory에게_히스토리를_전달한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> mockChannelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe();

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, mockChannelEntity, registryProbe.ref())
        );

        TestProbe<ClientSessionCommand> pendingReplyProbe = testKit.createTestProbe();
        TestProbe<ClientSessionCommand> immediateReplyProbe = testKit.createTestProbe();

        readerActor.tell(new RequestInitialHistory(pendingReplyProbe.ref()));

        LocalDateTime timestamp = LocalDateTime.now();
        List<ChatMessage> syncedMessages = List.of(
                ChatMessage.create(1L, 1L, 10L, "hello", timestamp, timestamp)
        );

        given(mockMessages.getMessages()).willReturn(syncedMessages);

        // when
        readerActor.tell(new ChannelReaderActor.DeliverSyncMessages(syncedMessages));

        // then
        FoundHistory foundHistoryFromPending = pendingReplyProbe.expectMessageClass(FoundHistory.class);
        assertThat(foundHistoryFromPending.history()).isEqualTo(syncedMessages);

        readerActor.tell(new RequestInitialHistory(immediateReplyProbe.ref()));
        FoundHistory foundHistoryAfterSync = immediateReplyProbe.expectMessageClass(FoundHistory.class);
        assertThat(foundHistoryAfterSync.history()).isEqualTo(syncedMessages);
    }

    @Test
    void 초기_동기화_전_도착한_SyncNewMessage는_대기하다가_동기화_완료_후에_전파된다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> mockChannelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe();
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe();

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, mockChannelEntity, registryProbe.ref())
        );

        readerActor.tell(new RegisterClientSession(100L, clientSessionProbe.ref()));

        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage pendingMessage = ChatMessage.create(1L, 200L, 11L, "pending", timestamp, timestamp);

        // when
        readerActor.tell(new SyncNewMessage(pendingMessage));

        // then
        clientSessionProbe.expectNoMessage(Duration.ofMillis(100));

        readerActor.tell(new ChannelReaderActor.DeliverSyncMessages(List.of()));

        DeliverNewMessage delivered = clientSessionProbe.expectMessageClass(DeliverNewMessage.class);
        assertThat(delivered.message()).isEqualTo(pendingMessage);
    }

    @Test
    void NotifyChangeChannelPolicy_메시지를_받으면_모든_ClientSessionActor에게_채널_정책_변경을_전파한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        readerActor.tell(new RegisterClientSession(100L, clientSessionProbe1.ref()));
        readerActor.tell(new RegisterClientSession(101L, clientSessionProbe2.ref()));

        ChannelPolicy channelPolicy = ChannelPolicy.defaultPolicy()
                                                   .updatePublic(false)
                                                   .updateEditOwnMessage(true)
                                                   .updateDeleteOwnMessage(false);

        // when
        readerActor.tell(new ChannelReaderActor.NotifyChangeChannelPolicy(channelPolicy));

        // then
        PropagateChangeChannelPolicy propagatedMessage1 = clientSessionProbe1.expectMessageClass(
                PropagateChangeChannelPolicy.class,
                Duration.ofSeconds(3)
        );
        PropagateChangeChannelPolicy propagatedMessage2 = clientSessionProbe2.expectMessageClass(
                PropagateChangeChannelPolicy.class,
                Duration.ofSeconds(3)
        );

        assertAll(
                () -> assertThat(propagatedMessage1.channelId()).isEqualTo(1L),
                () -> assertThat(propagatedMessage1.channelPolicy()).isEqualTo(channelPolicy),
                () -> assertThat(propagatedMessage2.channelId()).isEqualTo(1L),
                () -> assertThat(propagatedMessage2.channelPolicy()).isEqualTo(channelPolicy)
        );
    }

    @Test
    void NotifyMemberLeft_메시지를_받으면_해당_ClientSessionActor에게_채널_탈퇴를_전파한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        Long userId = 100L;
        readerActor.tell(new RegisterClientSession(userId, clientSessionProbe.ref()));

        // when
        readerActor.tell(new ChannelReaderActor.NotifyMemberLeft(userId));

        // then
        SyncLeaveChannel syncLeaveChannel = clientSessionProbe.expectMessageClass(
                SyncLeaveChannel.class,
                Duration.ofSeconds(3)
        );
        assertThat(syncLeaveChannel.channelId()).isEqualTo(1L);
    }

    @Test
    void NotifyKickedMember_메시지를_받으면_해당_ClientSessionActor에게_강퇴_알림을_전송하고_clientSessions에서_제거한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        Long userId = 100L;
        readerActor.tell(new RegisterClientSession(userId, clientSessionProbe.ref()));

        completeInitialSync(readerActor);

        // when
        readerActor.tell(new ChannelReaderActor.NotifyKickedMember(userId));

        // then
        PropagateKickedMember propagateKickedMember = clientSessionProbe.expectMessageClass(
                PropagateKickedMember.class,
                Duration.ofSeconds(3)
        );
        assertThat(propagateKickedMember.channelId()).isEqualTo(1L);

        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage newMessage = ChatMessage.create(1L, 1001L, 1L, "Test", timestamp, timestamp);
        readerActor.tell(new SyncNewMessage(newMessage));

        clientSessionProbe.expectNoMessage(Duration.ofMillis(100));
    }

    @Test
    void NotifyFailure_메시지를_받으면_해당_ClientSessionActor에게_에러를_전송한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        Long userId = 100L;
        readerActor.tell(new RegisterClientSession(userId, clientSessionProbe.ref()));

        String errorReason = "권한이 없습니다";

        // when
        readerActor.tell(new NotifyFailure(userId, errorReason));

        // then
        PropagateFailure propagateFailure = clientSessionProbe.expectMessageClass(
                PropagateFailure.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(propagateFailure.channelId()).isEqualTo(1L),
                () -> assertThat(propagateFailure.reason()).isEqualTo(errorReason)
        );
    }

    @Test
    void NotifyEditChannelName_메시지를_받으면_모든_ClientSessionActor에게_채널_이름_변경을_전파한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        readerActor.tell(new RegisterClientSession(100L, clientSessionProbe1.ref()));
        readerActor.tell(new RegisterClientSession(101L, clientSessionProbe2.ref()));

        String editedName = "새로운 채널명";

        // when
        readerActor.tell(new ChannelReaderActor.NotifyEditChannelName(editedName));

        // then
        PropagateEditChannelName propagatedMessage1 = clientSessionProbe1.expectMessageClass(
                PropagateEditChannelName.class,
                Duration.ofSeconds(3)
        );
        PropagateEditChannelName propagatedMessage2 = clientSessionProbe2.expectMessageClass(
                PropagateEditChannelName.class,
                Duration.ofSeconds(3)
        );

        assertAll(
                () -> assertThat(propagatedMessage1.channelId()).isEqualTo(1L),
                () -> assertThat(propagatedMessage1.editedName()).isEqualTo(editedName),
                () -> assertThat(propagatedMessage2.channelId()).isEqualTo(1L),
                () -> assertThat(propagatedMessage2.editedName()).isEqualTo(editedName)
        );
    }

    @Test
    void NotifyChangeChannelMembership_메시지를_받으면_해당_ClientSessionActor에게_멤버십_변경을_전파한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        Long userId = 100L;
        readerActor.tell(new RegisterClientSession(userId, clientSessionProbe.ref()));

        ChannelMembership membership = createManagerMembership(1L, 1L, userId);
        int membershipCount = 5;

        // when
        readerActor.tell(new ChannelReaderActor.NotifyChangeChannelMembership(userId, membership, membershipCount));

        // then
        PropagateChangeChannelMembership propagatedMessage = clientSessionProbe.expectMessageClass(
                PropagateChangeChannelMembership.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(propagatedMessage.channelId()).isEqualTo(1L),
                () -> assertThat(propagatedMessage.channelMembership()).isEqualTo(membership),
                () -> assertThat(propagatedMessage.membershipCount()).isEqualTo(membershipCount)
        );
    }

    @Test
    void NotifyMembershipCountChanged_메시지를_받으면_모든_ClientSessionActor에게_멤버십_카운트_변경을_전파한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        readerActor.tell(new RegisterClientSession(100L, clientSessionProbe1.ref()));
        readerActor.tell(new RegisterClientSession(101L, clientSessionProbe2.ref()));

        int membershipCount = 10;

        // when
        readerActor.tell(new NotifyMembershipCountChanged(membershipCount));

        // then
        PropagateMembershipCount propagatedMessage1 = clientSessionProbe1.expectMessageClass(
                PropagateMembershipCount.class,
                Duration.ofSeconds(3)
        );
        PropagateMembershipCount propagatedMessage2 = clientSessionProbe2.expectMessageClass(
                PropagateMembershipCount.class,
                Duration.ofSeconds(3)
        );

        assertAll(
                () -> assertThat(propagatedMessage1.channelId()).isEqualTo(1L),
                () -> assertThat(propagatedMessage1.membershipCount()).isEqualTo(membershipCount),
                () -> assertThat(propagatedMessage2.channelId()).isEqualTo(1L),
                () -> assertThat(propagatedMessage2.membershipCount()).isEqualTo(membershipCount)
        );
    }

    @Test
    void SyncMembership_메시지를_받으면_해당_ClientSessionActor에게_멤버십_정보를_전파한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        Long userId = 100L;
        readerActor.tell(new RegisterClientSession(userId, clientSessionProbe.ref()));

        ChannelMembership membership = createManagerMembership(1L, 1L, userId);
        int membershipCount = 7;

        // when
        readerActor.tell(new SyncMembership(userId, membership, membershipCount));

        // then
        PropagateChangeChannelMembership propagatedMessage = clientSessionProbe.expectMessageClass(
                PropagateChangeChannelMembership.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(propagatedMessage.channelId()).isEqualTo(1L),
                () -> assertThat(propagatedMessage.channelMembership()).isEqualTo(membership),
                () -> assertThat(propagatedMessage.membershipCount()).isEqualTo(membershipCount)
        );
    }

    @Test
    void SyncChannelMetadata_메시지를_받으면_모든_ClientSessionActor에게_채널_메타데이터를_전파한다() {
        // given
        ChannelReaderChatMessages mockMessages = mock(ChannelReaderChatMessages.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, registryProbe.ref())
        );

        readerActor.tell(new RegisterClientSession(100L, clientSessionProbe.ref()));

        String channelName = "테스트 채널";
        ChannelPolicy channelPolicy = ChannelPolicy.defaultPolicy();
        int membershipCount = 15;

        // when
        readerActor.tell(new SyncChannelMetadata(1L, channelName, channelPolicy, membershipCount));

        // then
        PropagateEditChannelName propagateEditChannelName = clientSessionProbe.expectMessageClass(
                PropagateEditChannelName.class,
                Duration.ofSeconds(3)
        );
        PropagateChangeChannelPolicy propagateChangeChannelPolicy = clientSessionProbe.expectMessageClass(
                PropagateChangeChannelPolicy.class,
                Duration.ofSeconds(3)
        );
        PropagateMembershipCount propagateMembershipCount = clientSessionProbe.expectMessageClass(
                PropagateMembershipCount.class,
                Duration.ofSeconds(3)
        );

        assertAll(
                () -> assertThat(propagateEditChannelName.channelId()).isEqualTo(1L),
                () -> assertThat(propagateEditChannelName.editedName()).isEqualTo(channelName),
                () -> assertThat(propagateChangeChannelPolicy.channelId()).isEqualTo(1L),
                () -> assertThat(propagateChangeChannelPolicy.channelPolicy()).isEqualTo(channelPolicy),
                () -> assertThat(propagateMembershipCount.channelId()).isEqualTo(1L),
                () -> assertThat(propagateMembershipCount.membershipCount()).isEqualTo(membershipCount)
        );
    }

    private ChannelMembership createManagerMembership(Long membershipId, Long channelId, Long userId) {
        Set<ChannelPermissionType> permissions = EnumSet.of(
                ChannelPermissionType.MESSAGE_EDIT,
                ChannelPermissionType.MEMBER_KICK
        );
        ChannelManagePermissions managePermissions = ChannelManagePermissions.ofManager(permissions);

        return ChannelMembership.create(
                membershipId,
                channelId,
                userId,
                ChannelRole.MANAGER,
                managePermissions,
                LocalDateTime.now()
        );
    }

    private void completeInitialSync(ActorRef<ChannelReaderCommand> readerActor) {
        readerActor.tell(new ChannelReaderActor.DeliverSyncMessages(List.of()));
    }
}
