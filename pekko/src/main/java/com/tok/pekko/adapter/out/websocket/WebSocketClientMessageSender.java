package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.in.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WebSocketClientMessageSender implements ClientMessageSender {

    @Override
    public void sendMessage(ChatMessage message) {
        // NO-OP
    }

    @Override
    public void sendMessages(List<ChatMessage> messages) {
        // NO-OP
    }

    @Override
    public void close() {
        // NO-OP
    }
}
