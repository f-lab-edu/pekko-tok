package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ChannelReaderRegistryActor.GetChannelReaderActor;
import com.tok.pekko.adapter.out.websocket.ClientSessionActor.FoundChannelReaders;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.GetHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.UnregisterClientSession;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundRegisteredChannelIds;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.RequestHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncJoinChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncLeaveChannel;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

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
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
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
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
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
        clientSessionActor.tell(new DeliverHistory(1L, 10L, 3, historyMessages));

        // then
        verify(mockClientMessageSender, timeout(1000)).sendMessages(historyMessages);
    }

    @Test
    void DeliverHistory_메시지로_빈_리스트를_받으면_MessageStoragePort로_히스토리를_조회한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        List<ChatMessage> emptyMessages = List.of();

        // when
        clientSessionActor.tell(new DeliverHistory(1L, 10L, 3, emptyMessages));

        // then
        verify(mockMessageStoragePort, timeout(1000)).findHistory(
                eq(1L), eq(10L), eq(3), any(ActorRef.class)
        );
    }

    @Test
    void FoundHistory_메시지를_받으면_ClientMessageSender로_조회된_히스토리를_전송한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        LocalDateTime timestamp1 = LocalDateTime.of(2025, 10, 17, 14, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 10, 17, 14, 0, 1);
        List<ChatMessage> foundMessages = Arrays.asList(
                new ChatMessage(1L, 1L, 100L, 1L, "Message 1", timestamp1, timestamp1),
                new ChatMessage(1L, 2L, 101L, 2L, "Message 2", timestamp2, timestamp2)
        );

        // when
        clientSessionActor.tell(new FoundHistory(foundMessages));

        // then
        verify(mockClientMessageSender, timeout(1000)).sendMessages(foundMessages);
    }

    @Test
    void RequestHistory_메시지를_받으면_해당_채널의_reader에_GetHistory가_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        Map<Long, ActorRef<ChannelReaderCommand>> readers = Map.of(1L, readerProbe.ref());

        clientSessionActor.tell(new FoundChannelReaders(readers));
        readerProbe.expectMessageClass(RegisterClientSession.class);

        // when
        clientSessionActor.tell(new RequestHistory(1L, 10L, 5));

        // then
        GetHistory getHistory = readerProbe.expectMessageClass(GetHistory.class);
        assertAll(
                () -> assertThat(getHistory.messageSequence()).isEqualTo(10L),
                () -> assertThat(getHistory.size()).isEqualTo(5)
        );
    }

    @Test
    void RequestHistory_메시지를_받을_때_해당_채널_reader가_없으면_readerRegistry에_요청한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe(ChannelReaderRegistryCommand.class);

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        // when
        clientSessionActor.tell(new RequestHistory(1L, 10L, 5));

        // then
        GetChannelReaderActor message = readerRegistryProbe.expectMessageClass(
                GetChannelReaderActor.class
        );
        assertThat(message.channelIds()).containsExactly(1L);
    }

    @Test
    void Shutdown_메시지를_받으면_액터가_종료된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        TestProbe<Void> terminationProbe = testKit.createTestProbe();

        // when
        clientSessionActor.tell(new Shutdown());

        // then
        terminationProbe.expectTerminated(clientSessionActor);
    }

    @Test
    void 여러_DeliverNewMessage_메시지를_연속으로_받으면_모두_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
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
    void DeliverDeletedMessage_메시지를_받으면_ClientMessageSender로_삭제된_메시지를_전송한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
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
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
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
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        List<Long> channelIds = List.of(1L, 2L, 3L);

        // when
        clientSessionActor.tell(new FoundRegisteredChannelIds(channelIds));

        // then
        GetChannelReaderActor actual = readerRegistryProbe.expectMessageClass(GetChannelReaderActor.class);

        assertThat(actual.channelIds()).containsAll(channelIds);
    }

    @Test
    void FoundChannelReaders_메시지를_받으면_readers에_등록되고_각_reader에_RegisterClientSession이_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        TestProbe<ChannelReaderCommand> reader1Probe = testKit.createTestProbe();
        TestProbe<ChannelReaderCommand> reader2Probe = testKit.createTestProbe();
        Map<Long, ActorRef<ChannelReaderCommand>> readers = Map.of(
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
    void SyncJoinChannel_메시지를_받으면_readerRegistry에_GetChannelReaderActorRef가_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        // when
        clientSessionActor.tell(new SyncJoinChannel(7L));

        // then
        GetChannelReaderActor actual = readerRegistryProbe.expectMessageClass(GetChannelReaderActor.class);

        assertThat(actual.channelIds()).contains(7L);
    }

    @Test
    void SyncLeaveChannel_메시지를_받으면_reader에서_제거되고_ChatChannelReaderActor에_UnregisterClientSession이_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        ChannelMembershipPort mockChannelMembershipPort = mock(ChannelMembershipPort.class);
        TestProbe<ChannelReaderRegistryCommand> readerRegistryProbe = testKit.createTestProbe();

        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(100L, mockClientMessageSender, mockMessageStoragePort, mockChannelMembershipPort, readerRegistryProbe.ref())
        );

        TestProbe<ChannelReaderCommand> readerProbe = testKit.createTestProbe();
        Map<Long, ActorRef<ChannelReaderCommand>> readers = Map.of(3L, readerProbe.ref());

        clientSessionActor.tell(new FoundChannelReaders(readers));
        readerProbe.expectMessageClass(RegisterClientSession.class);

        // when
        clientSessionActor.tell(new SyncLeaveChannel(3L));

        // then
        readerProbe.expectMessageClass(UnregisterClientSession.class);
    }
}
