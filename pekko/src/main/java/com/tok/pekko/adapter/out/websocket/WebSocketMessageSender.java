package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Sinks;

@RequiredArgsConstructor
public class WebSocketMessageSender implements ClientMessageSender {

    private final Sinks.Many<ChatMessage> sink;

    @Override
    public void sendMessage(ChatMessage message) {
        sink.tryEmitNext(message);
    }

    @Override
    public void sendDeletedMessage(ChatMessage deletedMessage) {
        sink.tryEmitNext(deletedMessage);
    }

    @Override
    public void sendMessages(List<ChatMessage> messages) {
        messages.forEach(sink::tryEmitNext);
    }
}
