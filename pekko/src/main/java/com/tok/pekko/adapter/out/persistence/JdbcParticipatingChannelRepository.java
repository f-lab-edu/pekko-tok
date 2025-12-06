package com.tok.pekko.adapter.out.persistence;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcParticipatingChannelRepository implements ParticipatingChannelRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcParticipatingChannelRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Long> findAllChannelIds(Long userId) {
        String sql = """
                SELECT cm.channel_id
                FROM channel_memberships cm
                INNER JOIN channels c ON c.id = cm.channel_id
                WHERE cm.user_id = :userId
                  AND c.is_deleted = false
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId);

        return jdbcTemplate.queryForList(sql, params, Long.class);
    }
}
