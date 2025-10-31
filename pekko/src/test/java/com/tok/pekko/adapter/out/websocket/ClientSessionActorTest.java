package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ChannelReaderRegistryActor.GetChannelReaderActorRef;
import com.tok.pekko.adapter.out.websocket.ClientSessionActor.FoundChannelReaders;
import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.UnregisterClientSession;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundRegisteredChannelIds;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.JoinChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.LeaveChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncJoinChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncLeaveChannel;
import java.util.Map;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ClientSessionActorTest {

    private static final ActorTestKit testKit = ActorTestKit.create();

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void DeliverNewMessage_메시지를_받으면_ClientMessageSender로_채팅_메시지를_전송한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        LocalDateTime timestamp = LocalDateTime.of(2025, 10, 17, 14, 0, 0);
        ChatMessage message = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                timestamp,
                timestamp
        );

        // when
        clientSessionActor.tell(new DeliverNewMessage(message));

        // then
        verify(mockClientMessageSender, timeout(1000)).sendMessage(message);
    }

    @Test
    void DeliverHistory_메시지를_받으면_ClientMessageSender로_히스토리_메시지들을_전송한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 14, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 14, 0, 1);
        LocalDateTime timestamp3 = LocalDateTime.of(2025, 10, 17, 14, 0, 2);
        List<ChatMessage> historyMessages = Arrays.asList(
                new ChatMessage(1L, 1L, 100L, 1L, "Message 1", timestamp1, timestamp1),
                new ChatMessage(1L, 2L, 101L, 2L, "Message 2", timestamp2, timestamp2),
                new ChatMessage(1L, 3L, 102L, 3L, "Message 3", timestamp3, timestamp3)
        );

        // when
        clientSessionActor.tell(new DeliverHistory(historyMessages));

        // then
        verify(mockClientMessageSender, timeout(1000)).sendMessages(historyMessages);
    }

    @Test
    void Shutdown_메시지를_받으면_액터가_종료된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );
        TestProbe<Void> terminationProbe = testKit.createTestProbe();

        // when
        clientSessionActor.tell(new Shutdown());

        // then
        terminationProbe.expectTerminated(clientSessionActor, Duration.ofSeconds(3));
    }

    @Test
    void 여러_DeliverNewMessage_메시지를_연속으로_받으면_모두_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 14, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 14, 0, 1);
        LocalDateTime timestamp3 = LocalDateTime.of(2025, 10, 17, 14, 0, 2);
        ChatMessage message1 = ChatMessage.create(1L, 100L, 1L, "First Message", timestamp1, timestamp1);
        ChatMessage message2 = ChatMessage.create(1L, 101L, 2L, "Second Message", timestamp2, timestamp2);
        ChatMessage message3 = ChatMessage.create(1L, 102L, 3L, "Third Message", timestamp3, timestamp3);

        // when
        clientSessionActor.tell(new DeliverNewMessage(message1));
        clientSessionActor.tell(new DeliverNewMessage(message2));
        clientSessionActor.tell(new DeliverNewMessage(message3));

        // then
        assertAll(
                () -> verify(mockClientMessageSender, timeout(1000)).sendMessage(message1),
                () -> verify(mockClientMessageSender, timeout(1000)).sendMessage(message2),
                () -> verify(mockClientMessageSender, timeout(1000)).sendMessage(message3)
        );
    }

    @Test
    void DeliverHistory_메시지로_빈_리스트를_받으면_빈_리스트가_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        List<ChatMessage> emptyMessages = List.of();

        // when
        clientSessionActor.tell(new DeliverHistory(emptyMessages));

        // then
        verify(mockClientMessageSender, timeout(1000)).sendMessages(emptyMessages);
    }

    @Test
    void DeliverDeletedMessage_메시지를_받으면_ClientMessageSender로_삭제된_메시지를_전송한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        LocalDateTime timestamp = LocalDateTime.of(2025, 10, 17, 14, 0, 0);
        ChatMessage deletedMessage = new ChatMessage(1L, 1L, 100L, 1L, "Deleted Message", timestamp, timestamp);

        // when
        clientSessionActor.tell(new DeliverDeletedMessage(deletedMessage));

        // then
        verify(mockClientMessageSender, timeout(1000)).sendDeletedMessage(deletedMessage);
    }

    @Test
    void DeliverUpdatedMessage_메시지를_받으면_ClientMessageSender로_수정된_메시지를_전송한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        LocalDateTime timestamp = LocalDateTime.of(2025, 10, 17, 14, 0, 0);
        ChatMessage updatedMessage = new ChatMessage(1L, 1L, 100L, 1L, "Updated Message", timestamp, timestamp);

        // when
        clientSessionActor.tell(new DeliverUpdatedMessage(updatedMessage));

        // then
        verify(mockClientMessageSender, timeout(1000)).sendMessage(updatedMessage);
    }

    @Test
    void FoundRegisteredChannelIds_메시지를_받으면_readerRegistry에_GetChannelReaderActorRef가_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        List<Long> channelIds = List.of(1L, 2L, 3L);

        // when
        clientSessionActor.tell(new FoundRegisteredChannelIds(channelIds));

        // then
        GetChannelReaderActorRef actual = readerRegistryProbe.expectMessageClass(GetChannelReaderActorRef.class);

        assertThat(actual.channelIds()).containsAll(channelIds);
    }

    @Test
    void FoundChannelReaders_메시지를_받으면_readers에_등록되고_각_reader에_RegisterClientSession이_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        TestProbe<ChatChannelReaderCommand> reader1Probe = testKit.createTestProbe();
        TestProbe<ChatChannelReaderCommand> reader2Probe = testKit.createTestProbe();
        Map<Long, ActorRef<ChatChannelReaderCommand>> readers = Map.of(
                1L, reader1Probe.ref(),
                2L, reader2Probe.ref()
        );

        // when
        clientSessionActor.tell(new ClientSessionActor.FoundChannelReaders(readers));

        // then
        assertAll(
                () -> reader1Probe.expectMessageClass(RegisterClientSession.class),
                () -> reader2Probe.expectMessageClass(RegisterClientSession.class)
        );
    }

    @Test
    void JoinChannel_메시지를_받으면_channelMembershipPort로_채널_가입을_전달한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        // when
        clientSessionActor.tell(new JoinChannel(5L));

        // then
        verify(mockChannelMembershipPort, timeout(1000)).joinChannel(eq(100L), eq(5L), any(ActorRef.class));
    }

    @Test
    void SyncJoinChannel_메시지를_받으면_readerRegistry에_GetChannelReaderActorRef가_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        // when
        clientSessionActor.tell(new SyncJoinChannel(7L));

        // then
        GetChannelReaderActorRef actual = readerRegistryProbe.expectMessageClass(GetChannelReaderActorRef.class);

        assertThat(actual.channelIds()).contains(7L);
    }

    @Test
    void LeaveChannel_메시지를_받으면_channelMembershipPort에_채널_탈퇴를_전달한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        // when
        clientSessionActor.tell(new LeaveChannel(3L));

        // then
        verify(mockChannelMembershipPort, timeout(1000)).leaveChannel(eq(100L), eq(3L), any(ActorRef.class));
    }

    @Test
    void SyncLeaveChannel_메시지를_받으면_reader에서_제거되고_ChatChannelReaderActor에_UnregisterClientSession이_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        TestProbe<ChatChannelReaderCommand> readerProbe = testKit.createTestProbe();
        Map<Long, ActorRef<ChatChannelReaderCommand>> readers = Map.of(3L, readerProbe.ref());

        clientSessionActor.tell(new FoundChannelReaders(readers));
        readerProbe.expectMessageClass(RegisterClientSession.class);

        // when
        clientSessionActor.tell(new SyncLeaveChannel(3L));

        // then
        readerProbe.expectMessageClass(UnregisterClientSession.class);
    }
}
