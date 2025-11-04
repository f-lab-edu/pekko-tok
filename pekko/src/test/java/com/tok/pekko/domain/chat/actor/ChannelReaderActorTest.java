package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.chat.actor.ChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.GetHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.PongHealthCheck;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.UnregisterClientSession;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
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

@SuppressWarnings("NonAsciiCharacters")
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
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, clientSessionProbe.ref(), registryProbe.ref())
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
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> replyToProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, clientSessionProbe.ref(), registryProbe.ref())
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
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> replyToProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, clientSessionProbe.ref(), registryProbe.ref())
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
    void Shutdown_메시지를_받으면_ChatChannelReaderActor가_종료된다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, clientSessionProbe.ref(), registryProbe.ref())
        );

        // when
        readerActor.tell(new Shutdown());

        // then
        TestProbe<Void> terminationProbe = testKit.createTestProbe();
        terminationProbe.expectTerminated(readerActor, Duration.ofSeconds(3));
    }

    @Test
    void SyncDeletion_메시지를_받으면_ChatMessages에서_메시지를_삭제하고_모든_ClientSessionActor에_전달한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, clientSessionProbe.ref(), registryProbe.ref())
        );

        Long messageId = 1L;
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage deletedMessage = new ChatMessage(
                messageId,
                1L,
                1001L,
                1L,
                "Deleted Message",
                timestamp,
                timestamp
        );

        readerActor.tell(new RegisterClientSession(100L, clientSessionProbe1.ref()));
        readerActor.tell(new RegisterClientSession(101L, clientSessionProbe2.ref()));

        given(mockMessages.delete(messageId)).willReturn(deletedMessage);

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
                () -> assertThat(deliveredDeletedMessage1.deletedMessage()).isEqualTo(deletedMessage),
                () -> assertThat(deliveredDeletedMessage2.deletedMessage()).isEqualTo(deletedMessage)
        );
    }

    @Test
    void SyncUpdate_메시지를_받으면_ChatMessages에서_메시지를_수정하고_모든_ClientSessionActor에_전달한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe1 = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ClientSessionCommand> clientSessionProbe2 = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, clientSessionProbe.ref(), registryProbe.ref())
        );

        Long messageId = 1L;
        String updatedMessageContent = "Updated Message";
        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage updatedMessage = new ChatMessage(
                messageId,
                1L,
                1001L,
                1L,
                updatedMessageContent,
                timestamp,
                timestamp
        );

        readerActor.tell(new RegisterClientSession(100L, clientSessionProbe1.ref()));
        readerActor.tell(new RegisterClientSession(101L, clientSessionProbe2.ref()));

        given(mockMessages.update(eq(messageId), eq(updatedMessageContent), any(LocalDateTime.class))).willReturn(updatedMessage);

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
                () -> assertThat(deliveredUpdatedMessage1.updatedMessage()).isEqualTo(updatedMessage),
                () -> assertThat(deliveredUpdatedMessage2.updatedMessage()).isEqualTo(updatedMessage)
        );
    }

    @Test
    void RegisterClientSession_메시지를_받으면_clientSessions에_등록된다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> registeredSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, clientSessionProbe.ref(), registryProbe.ref())
        );

        Long userId = 100L;

        // when
        readerActor.tell(new RegisterClientSession(userId, registeredSessionProbe.ref()));

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
    void UnregisterClientSession_메시지를_받으면_clientSessions에서_제거된다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> registeredSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, clientSessionProbe.ref(), registryProbe.ref())
        );

        Long userId = 100L;
        readerActor.tell(new RegisterClientSession(userId, registeredSessionProbe.ref()));

        // when
        readerActor.tell(new UnregisterClientSession(userId));

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

        registeredSessionProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void PongHealthCheck_메시지를_받으면_헬스체크_타임아웃이_발생하지_않는다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        TestProbe<ChannelReaderRegistryCommand> registryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);
        TestProbe<ClientSessionCommand> registeredSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);

        ActorRef<ChannelReaderCommand> readerActor = testKit.spawn(
                ChannelReaderActor.create(1L, mockMessages, channelEntity, clientSessionProbe.ref(), registryProbe.ref())
        );

        Long userId = 100L;
        readerActor.tell(new RegisterClientSession(userId, registeredSessionProbe.ref()));

        LocalDateTime timestamp = LocalDateTime.now();
        ChatMessage newMessage = ChatMessage.create(
                1L,
                1001L,
                1L,
                "Test",
                timestamp,
                timestamp
        );

        // when
        readerActor.tell(new PongHealthCheck(userId));

        // then
        readerActor.tell(new SyncNewMessage(newMessage));

        DeliverNewMessage actual = registeredSessionProbe.expectMessageClass(
                DeliverNewMessage.class,
                Duration.ofSeconds(3)
        );
        assertThat(actual.message()).isEqualTo(newMessage);
    }
}
