package com.tok.pekko.adapter.in.actor;

import com.tok.pekko.adapter.in.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown;
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

import static org.junit.jupiter.api.Assertions.assertAll;
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
    void DeliverCommand_메시지를_받으면_ClientMessageSender로_채팅_메시지를_전송한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(mockClientMessageSender)
        );

        ChatMessage message = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 14, 0, 0)
        );

        // when
        clientSessionActor.tell(new DeliverCommand(message));

        // then
        verify(mockClientMessageSender, timeout(1000)).sendMessage(message);
    }

    @Test
    void DeliverHistory_메시지를_받으면_ClientMessageSender로_히스토리_메시지들을_전송한다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(mockClientMessageSender)
        );

        List<ChatMessage> historyMessages = Arrays.asList(
                new ChatMessage(1L, 1L, 100L, 1L, "Message 1", LocalDateTime.of(2025, 10, 17, 14, 0, 0)),
                new ChatMessage(1L, 2L, 101L, 2L, "Message 2", LocalDateTime.of(2025, 10, 17, 14, 0, 1)),
                new ChatMessage(1L, 3L, 102L, 3L, "Message 3", LocalDateTime.of(2025, 10, 17, 14, 0, 2))
        );

        // when
        clientSessionActor.tell(new DeliverHistory(historyMessages));

        // then
        verify(mockClientMessageSender, timeout(1000)).sendMessages(historyMessages);
    }

    @Test
    void Shutdown_메시지를_받으면_ClientMessageSender를_종료하고_액터가_종료된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(mockClientMessageSender)
        );

        TestProbe<Void> terminationProbe = testKit.createTestProbe();

        // when
        clientSessionActor.tell(new Shutdown());

        // then
        verify(mockClientMessageSender, timeout(1000)).close();
        terminationProbe.expectTerminated(clientSessionActor, Duration.ofSeconds(3));
    }

    @Test
    void 여러_DeliverCommand_메시지를_연속으로_받으면_모두_전송된다() {
        // given
        ClientMessageSender mockClientMessageSender = mock(ClientMessageSender.class);
        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(mockClientMessageSender)
        );

        ChatMessage message1 = ChatMessage.create(
                1L,
                100L,
                1L,
                "First Message",
                LocalDateTime.of(2025, 10, 17, 14, 0, 0)
        );
        ChatMessage message2 = ChatMessage.create(
                1L,
                101L,
                2L,
                "Second Message",
                LocalDateTime.of(2025, 10, 17, 14, 0, 1)
        );
        ChatMessage message3 = ChatMessage.create(
                1L,
                102L,
                3L,
                "Third Message",
                LocalDateTime.of(2025, 10, 17, 14, 0, 2)
        );

        // when
        clientSessionActor.tell(new DeliverCommand(message1));
        clientSessionActor.tell(new DeliverCommand(message2));
        clientSessionActor.tell(new DeliverCommand(message3));

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
        ActorRef<ClientSessionCommand> clientSessionActor = testKit.spawn(
                ClientSessionActor.create(mockClientMessageSender)
        );

        List<ChatMessage> emptyMessages = List.of();

        // when
        clientSessionActor.tell(new DeliverHistory(emptyMessages));

        // then
        verify(mockClientMessageSender, timeout(1000)).sendMessages(emptyMessages);
    }
}
