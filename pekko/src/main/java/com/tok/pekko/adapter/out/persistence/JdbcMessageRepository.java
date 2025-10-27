package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JdbcMessageRepository implements MessageRepository {

    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        // NO-OP
        return chatMessage;
    }

    @Override
    public void delete(Long messageId) {
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
