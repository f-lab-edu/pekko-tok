package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.domain.chat.actor.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WebSocketMessageSenderTest {

    @Test
    void 단일_메시지를_클라이언트에게_전송한다() {
        // given
        @SuppressWarnings("unchecked")
        Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        ChatMessage message = createMessage(1L, "테스트 메시지");

        // when
        sender.sendMessage(message);

        // then
        verify(sink).tryEmitNext(new WebSocketMessageSender.WebSocketPayload(WebSocketMessageSender.EVENT_NEW, message));
    }

    @Test
    void 여러_메시지를_클라이언트에게_전송한다() {
        // given
        @SuppressWarnings("unchecked")
        Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink = mock(Sinks.Many.class);
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
                () -> verify(sink).tryEmitNext(new WebSocketMessageSender.WebSocketPayload(WebSocketMessageSender.EVENT_NEW, messages.get(0))),
                () -> verify(sink).tryEmitNext(new WebSocketMessageSender.WebSocketPayload(WebSocketMessageSender.EVENT_NEW, messages.get(1))),
                () -> verify(sink).tryEmitNext(new WebSocketMessageSender.WebSocketPayload(WebSocketMessageSender.EVENT_NEW, messages.get(2)))
        );
    }

    @Test
    void 삭제된_메시지를_클라이언트에게_전송한다() {
        // given
        @SuppressWarnings("unchecked")
        Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        ChatMessage deletedMessage = createMessage(1L, "삭제된 메시지");

        // when
        sender.sendDeletedMessage(deletedMessage);

        // then
        verify(sink).tryEmitNext(new WebSocketMessageSender.WebSocketPayload(WebSocketMessageSender.EVENT_DELETED, deletedMessage));
    }

    @Test
    void 수정된_메시지를_클라이언트에게_전송한다() {
        // given
        @SuppressWarnings("unchecked")
        Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);
        ChatMessage updatedMessage = createMessage(1L, "수정된 메시지");

        // when
        sender.sendUpdatedMessage(updatedMessage);

        // then
        verify(sink).tryEmitNext(new WebSocketMessageSender.WebSocketPayload(WebSocketMessageSender.EVENT_UPDATED, updatedMessage));
    }

    @Test
    void 웹소켓_헬스체크_핑을_전송한다() {
        @SuppressWarnings("unchecked")
        Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);

        sender.sendWebSocketPing();

        verify(sink).tryEmitNext(new WebSocketMessageSender.WebSocketPayload(WebSocketMessageSender.EVENT_WS_PING, null));
    }

    @Test
    void 재연결을_요청한다() {
        @SuppressWarnings("unchecked")
        Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);

        sender.requestSessionReconnect();

        verify(sink).tryEmitNext(new WebSocketMessageSender.WebSocketPayload(WebSocketMessageSender.EVENT_WS_RECONNECT, null));
    }

    @Test
    void sink를_교체하면_새로운_sink로_메시지를_전송한다() {
        @SuppressWarnings("unchecked")
        Sinks.Many<WebSocketMessageSender.WebSocketPayload> firstSink = mock(Sinks.Many.class);
        @SuppressWarnings("unchecked")
        Sinks.Many<WebSocketMessageSender.WebSocketPayload> secondSink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(firstSink);
        ChatMessage message = createMessage(10L, "hello");

        sender.sendMessage(message);
        sender.attachSink(secondSink);
        sender.sendMessage(message);

        verify(firstSink, times(1)).tryEmitNext(new WebSocketMessageSender.WebSocketPayload(WebSocketMessageSender.EVENT_NEW, message));
        verify(secondSink, times(1)).tryEmitNext(new WebSocketMessageSender.WebSocketPayload(WebSocketMessageSender.EVENT_NEW, message));
    }

    @Test
    void sink를_detach하면_추가_메시지가_전송되지_않는다() {
        @SuppressWarnings("unchecked")
        Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink = mock(Sinks.Many.class);
        WebSocketMessageSender sender = new WebSocketMessageSender(sink);

        sender.detachSink(sink);
        sender.requestSessionReconnect();

        verifyNoInteractions(sink);
    }

    private ChatMessage createMessage(Long messageId, String content) {
        return ChatMessage.create(
                1L,
                100L,
                messageId,
                content,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
