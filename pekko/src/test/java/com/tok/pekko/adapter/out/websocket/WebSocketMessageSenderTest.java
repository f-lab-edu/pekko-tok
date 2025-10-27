package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.domain.chat.model.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WebSocketMessageSenderTest {

    @Test
    void 단일_메시지를_클라이언트에게_전송한다() {
        // given
        @SuppressWarnings("unchecked")
        Sinks.Many<ChatMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        ChatMessage message = createMessage(1L, "테스트 메시지");

        // when
        sender.sendMessage(message);

        // then
        verify(sink).tryEmitNext(message);
    }

    @Test
    void 여러_메시지를_클라이언트에게_전송한다() {
        // given
        @SuppressWarnings("unchecked")
        Sinks.Many<ChatMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        List<ChatMessage> messages = List.of(
                createMessage(1L, "메시지1"),
                createMessage(2L, "메시지2"),
                createMessage(3L, "메시지3")
        );

        // when
        sender.sendMessages(messages);

        // then
        assertAll(
                () -> verify(sink).tryEmitNext(messages.get(0)),
                () -> verify(sink).tryEmitNext(messages.get(1)),
                () -> verify(sink).tryEmitNext(messages.get(2))
        );
    }

    @Test
    void 삭제된_메시지를_클라이언트에게_전송한다() {
        // given
        @SuppressWarnings("unchecked")
        Sinks.Many<ChatMessage> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        ChatMessage deletedMessage = createMessage(1L, "삭제된 메시지");

        // when
        sender.sendDeletedMessage(deletedMessage);

        // then
        verify(sink).tryEmitNext(deletedMessage);
    }

    private ChatMessage createMessage(Long messageId, String content) {
        return ChatMessage.create(
                1L,
                100L,
                messageId,
                content,
                LocalDateTime.now()
        );
    }
}
