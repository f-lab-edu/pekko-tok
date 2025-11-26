package com.tok.pekko.adapter.out.persistence;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcParticipatingChannelRepository implements ParticipatingChannelRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<Long> findAllChannelIds(Long userId) {
        String sql = """
                SELECT channel_id
                FROM channel_memberships
                WHERE user_id = :userId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getLong("channel_id")
        );
    }
}
