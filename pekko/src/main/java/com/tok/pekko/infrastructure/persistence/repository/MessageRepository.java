package com.tok.pekko.infrastructure.persistence.repository;

import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;

public interface MessageRepository {

    void save(ChatMessage chatMessage);

    List<ChatMessage> findHistory(Long channelId, long messageSequence, int size);

    List<ChatMessage> findLatest(Long channelId, int size);
}
