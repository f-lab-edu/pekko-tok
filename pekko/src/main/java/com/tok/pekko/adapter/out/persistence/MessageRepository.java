package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;

public interface MessageRepository {

    ChatMessage save(ChatMessage chatMessage);

    void delete(Long messageId);

    List<ChatMessage> findHistory(Long channelId, long messageSequence, int size);

    List<ChatMessage> findLatest(Long channelId, int size);
}
