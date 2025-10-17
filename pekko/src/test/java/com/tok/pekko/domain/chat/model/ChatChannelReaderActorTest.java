package com.tok.pekko.domain.chat.model;

import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.HistoryLoaded;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.RequestHistory;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncNewCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatChannelReaderActorTest {

    private static final ActorTestKit testKit = ActorTestKit.create();

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void SyncNewCommand_메시지를_받으면_채팅_메시지를_ChatMessages에_추가하고_ClientSessionActor에_전달한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(1L, mockMessages, mockMessageStoragePort, clientSessionProbe.ref())
        );

        ChatMessage newMessage = ChatMessage.create(
                1L,
                1001L,
                1L,
                "Hello World",
                LocalDateTime.now()
        );

        // when
        readerActor.tell(new SyncNewCommand(newMessage));

        // then
        verify(mockMessages, timeout(1000)).add(newMessage);

        DeliverCommand deliveredCommand = clientSessionProbe.expectMessageClass(
                DeliverCommand.class,
                Duration.ofSeconds(3)
        );
        assertThat(deliveredCommand.message()).isEqualTo(newMessage);
    }

    @Test
    void LoadHistory_메시지를_받으면_메모리에_있는_메시지를_조회해_ClientSessionActor에_전달한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(1L, mockMessages, mockMessageStoragePort, clientSessionProbe.ref())
        );

        long messageSequence = 100L;
        int size = 10;
        List<ChatMessage> historyMessages = List.of(
                new ChatMessage(1L, 90L, 2001L, 90L, "Message 90", LocalDateTime.now()),
                new ChatMessage(1L, 91L, 2002L, 91L, "Message 91", LocalDateTime.now()),
                new ChatMessage(1L, 92L, 2001L, 92L, "Message 92", LocalDateTime.now())
        );

        given(mockMessages.getHistory(messageSequence, size)).willReturn(historyMessages);

        // when
        readerActor.tell(new RequestHistory(messageSequence, size));

        // then
        assertAll(
                () -> verify(mockMessages, timeout(1000)).getHistory(messageSequence, size),
                () -> verify(mockMessageStoragePort, never()).findHistory(anyLong(), anyLong(), anyInt(), any())
        );

        DeliverHistory deliveredHistory = clientSessionProbe.expectMessageClass(
                DeliverHistory.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(deliveredHistory.messages()).hasSize(3),
                () -> assertThat(deliveredHistory.messages()).isEqualTo(historyMessages)
        );
    }

    @Test
    void LoadHistory_메시지를_받았을_때_메모리가_비어있으면_Storage에_조회를_요청한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(1L, mockMessages, mockMessageStoragePort, clientSessionProbe.ref())
        );

        long messageSequence = 10L;
        int size = 5;
        List<ChatMessage> storageMessages = Arrays.asList(
                new ChatMessage(1L, 5L, 3001L, 5L, "Storage Message 5", LocalDateTime.now()),
                new ChatMessage(1L, 6L, 3002L, 6L, "Storage Message 6", LocalDateTime.now())
        );

        given(mockMessages.getHistory(messageSequence, size)).willReturn(List.of());

        // when
        readerActor.tell(new RequestHistory(messageSequence, size));

        // then
        assertAll(
                () -> verify(mockMessages, timeout(1000)).getHistory(messageSequence, size),
                () -> verify(mockMessageStoragePort, timeout(1000)).findHistory(eq(1L), eq(messageSequence), eq(size), eq(readerActor))
        );

        DeliverHistory deliveredHistory1 = clientSessionProbe.expectMessageClass(
                DeliverHistory.class,
                Duration.ofSeconds(3)
        );
        assertThat(deliveredHistory1.messages()).isEmpty();

        readerActor.tell(new HistoryLoaded(storageMessages));

        DeliverHistory deliveredHistory2 = clientSessionProbe.expectMessageClass(
                DeliverHistory.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(deliveredHistory2.messages()).hasSize(2),
                () -> assertThat(deliveredHistory2.messages()).isEqualTo(storageMessages)
        );
    }

    @Test
    void LoadHistory_메시지를_받았을_때_메모리와_Storage_모두_비어있으면_빈_리스트를_전달한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(1L, mockMessages, mockMessageStoragePort, clientSessionProbe.ref())
        );

        long messageSequence = 10L;
        int size = 5;
        given(mockMessages.getHistory(messageSequence, size)).willReturn(List.of());

        // when
        readerActor.tell(new RequestHistory(messageSequence, size));

        // then
        assertAll(
                () -> verify(mockMessages, timeout(1000)).getHistory(messageSequence, size),
                () -> verify(mockMessageStoragePort, timeout(1000)).findHistory(eq(1L), eq(messageSequence), eq(size), eq(readerActor))
        );

        DeliverHistory deliveredHistory1 = clientSessionProbe.expectMessageClass(
                DeliverHistory.class,
                Duration.ofSeconds(3)
        );
        assertThat(deliveredHistory1.messages()).isEmpty();

        readerActor.tell(new HistoryLoaded(List.of()));

        DeliverHistory deliveredHistory2 = clientSessionProbe.expectMessageClass(
                DeliverHistory.class,
                Duration.ofSeconds(3)
        );
        assertThat(deliveredHistory2.messages()).isEmpty();
    }

    @Test
    void ReceiveHistory_메시지를_받으면_ClientSessionActor에_히스토리를_전달한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(1L, mockMessages, mockMessageStoragePort, clientSessionProbe.ref())
        );

        List<ChatMessage> storageMessages = Arrays.asList(
                new ChatMessage(1L, 10L, 9001L, 10L, "Message 10", LocalDateTime.now()),
                new ChatMessage(1L, 11L, 9002L, 11L, "Message 11", LocalDateTime.now())
        );

        // when
        readerActor.tell(new HistoryLoaded(storageMessages));

        // then
        DeliverHistory deliveredHistory = clientSessionProbe.expectMessageClass(
                DeliverHistory.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(deliveredHistory.messages()).hasSize(2),
                () -> assertThat(deliveredHistory.messages()).isEqualTo(storageMessages)
        );
    }

    @Test
    void Shutdown_메시지를_받으면_ClientSessionActor에_Shutdown을_전달하고_ChatChannelReaderActor가_종료된다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(1L, mockMessages, mockMessageStoragePort, clientSessionProbe.ref())
        );

        // when
        readerActor.tell(new Shutdown());

        // then
        clientSessionProbe.expectMessageClass(
                ClientSessionProtocol.Shutdown.class,
                Duration.ofSeconds(3)
        );

        TestProbe<Void> terminationProbe = testKit.createTestProbe();
        terminationProbe.expectTerminated(readerActor, Duration.ofSeconds(3));
    }

    @Test
    void LoadHistory_메모리에_일부만_있을_때_Storage를_조회하지_않는다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        MessageStoragePort mockMessageStoragePort = mock(MessageStoragePort.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(1L, mockMessages, mockMessageStoragePort, clientSessionProbe.ref())
        );

        long messageSequence = 200L;
        int size = 50;
        List<ChatMessage> partialMessages = Arrays.asList(
                new ChatMessage(1L, 195L, 7001L, 195L, "Partial 1", LocalDateTime.now()),
                new ChatMessage(1L, 196L, 7002L, 196L, "Partial 2", LocalDateTime.now())
        );
        given(mockMessages.getHistory(messageSequence, size)).willReturn(partialMessages);

        // when
        readerActor.tell(new RequestHistory(messageSequence, size));

        // then
        assertAll(
                () -> verify(mockMessages, timeout(1000)).getHistory(messageSequence, size),
                () -> verify(mockMessageStoragePort, never()).findHistory(anyLong(), anyLong(), anyInt(), any())
        );

        DeliverHistory deliveredHistory = clientSessionProbe.expectMessageClass(
                DeliverHistory.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(deliveredHistory.messages()).hasSize(2),
                () -> assertThat(deliveredHistory.messages()).isEqualTo(partialMessages)
        );
    }
}
