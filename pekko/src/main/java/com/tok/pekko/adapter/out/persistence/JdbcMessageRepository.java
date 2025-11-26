package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.actor.ChatMessage;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcMessageRepository implements MessageRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    private final RowMapper<ChatMessage> rowMapper = (rs, rowNum) -> new ChatMessage(
            rs.getLong("id"),
            rs.getLong("channel_id"),
            rs.getLong("user_id"),
            rs.getLong("order_sequence"),
            rs.getString("message"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                            INSERT INTO messages (
                                channel_id, user_id, order_sequence, message, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    new String[]{"id"}
            );
            ps.setLong(1, chatMessage.channelId());
            ps.setLong(2, chatMessage.userId());
            ps.setLong(3, chatMessage.orderSequence());
            ps.setString(4, chatMessage.message());
            ps.setTimestamp(5, Timestamp.valueOf(chatMessage.createdAt()));
            ps.setTimestamp(6, Timestamp.valueOf(chatMessage.updatedAt()));
            return ps;
        }, keyHolder);

        Long generatedId = keyHolder.getKey().longValue();
        return new ChatMessage(
                generatedId,
                chatMessage.channelId(),
                chatMessage.userId(),
                chatMessage.orderSequence(),
                chatMessage.message(),
                chatMessage.createdAt(),
                chatMessage.updatedAt()
        );
    }

    @Override
    public void update(Long messageId, String updatedMessage) {
        jdbcTemplate.update(
                "UPDATE messages SET message = ?, updated_at = ? WHERE id = ?",
                updatedMessage,
                Timestamp.valueOf(LocalDateTime.now(clock)),
                messageId
        );
    }

    @Override
    public void delete(Long messageId) {
        jdbcTemplate.update("DELETE FROM messages WHERE id = ?", messageId);
    }

    @Override
    public List<ChatMessage> findHistory(Long channelId, long messageSequence, int size) {
        List<ChatMessage> history = jdbcTemplate.query(
                """
                        SELECT id, channel_id, user_id, order_sequence, message, created_at, updated_at
                        FROM messages
                        WHERE channel_id = ? AND order_sequence < ?
                        ORDER BY order_sequence DESC
                        LIMIT ?
                        """,
                rowMapper,
                channelId,
                messageSequence,
                size
        );
        history.sort((a, b) -> Long.compare(a.orderSequence(), b.orderSequence()));
        return history;
    }

    @Override
    public List<ChatMessage> findLatest(Long channelId, int size) {
        return jdbcTemplate.query(
                """
                        SELECT id, channel_id, user_id, order_sequence, message, created_at, updated_at
                        FROM messages
                        WHERE channel_id = ?
                        ORDER BY order_sequence DESC
                        LIMIT ?
                        """,
                rowMapper,
                channelId,
                size
        );
    }
}
