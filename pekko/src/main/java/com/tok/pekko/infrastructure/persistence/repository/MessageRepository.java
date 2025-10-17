package com.tok.pekko.infrastructure.persistence.repository;

import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;

public interface MessageRepository {

    void save(ChatMessage chatMessage);

    List<ChatMessage> findAll(Long channelId, long messageSequence, int size);
}
