package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.domain.chat.actor.ChatMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Sinks;

public class WebSocketMessageSender implements ClientMessageSender {

    public static final String EVENT_NEW = "NEW";
    public static final String EVENT_UPDATED = "UPDATED";
    public static final String EVENT_DELETED = "DELETED";
    public static final String EVENT_WS_PING = "WS_HEALTH_PING";
    public static final String EVENT_WS_RECONNECT = "WS_RECONNECT";

    private final AtomicReference<Sinks.Many<WebSocketPayload>> sinkHolder;

    public WebSocketMessageSender() {
        this(null);
    }

    public WebSocketMessageSender(Sinks.Many<WebSocketPayload> sink) {
        this.sinkHolder = new AtomicReference<>(sink);
    }

    public void attachSink(Sinks.Many<WebSocketPayload> sink) {
        sinkHolder.set(sink);
    }

    public void detachSink(Sinks.Many<WebSocketPayload> sink) {
        sinkHolder.compareAndSet(sink, null);
    }

    @Override
    public void sendMessage(ChatMessage message) {
        emit(new WebSocketPayload(EVENT_NEW, message));
    }

    @Override
    public void sendMessages(List<ChatMessage> messages) {
        messages.forEach(this::sendMessage);
    }

    @Override
    public void sendDeletedMessage(ChatMessage deletedMessage) {
        emit(new WebSocketPayload(EVENT_DELETED, deletedMessage));
    }

    @Override
    public void sendUpdatedMessage(ChatMessage updatedMessage) {
        emit(new WebSocketPayload(EVENT_UPDATED, updatedMessage));
    }

    @Override
    public void sendWebSocketPing() {
        emit(new WebSocketPayload(EVENT_WS_PING, null));
    }

    @Override
    public void requestSessionReconnect() {
        emit(new WebSocketPayload(EVENT_WS_RECONNECT, null));
    }

    private void emit(WebSocketPayload payload) {
        Sinks.Many<WebSocketPayload> sink = sinkHolder.get();

        if (sink != null) {
            sink.tryEmitNext(payload);
        }
    }

    public record WebSocketPayload(String type, ChatMessage message) { }
}
