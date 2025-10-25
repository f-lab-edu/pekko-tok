package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;

public interface ClientMessageSender {

    void sendMessage(ChatMessage message);

    void sendMessages(List<ChatMessage> messages);
}
