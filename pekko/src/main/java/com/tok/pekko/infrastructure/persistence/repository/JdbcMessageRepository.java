package com.tok.pekko.infrastructure.persistence.repository;

import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JdbcMessageRepository implements MessageRepository {

    @Override
    public void save(ChatMessage chatMessage) {
        // NO-OP
    }

    @Override
    public List<ChatMessage> findHistory(Long channelId, long messageSequence, int size) {
        // NO-OP
        return List.of();
    }

    @Override
    public List<ChatMessage> findLatest(Long channelId, int size) {
        // NO-OP
        return List.of();
    }
}
