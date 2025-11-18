package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.application.port.out.dto.ChannelMemberDto;
import com.tok.pekko.domain.channel.model.ChannelRole;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcChannelMemberViewRepository implements ChannelMemberViewRepository {

    private static final RowMapper<ChannelMemberDto> channelMembershipDtoRowMapper = (rs, rowNum) ->
            new ChannelMemberDto(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    ChannelRole.find(rs.getString("channel_role")),
                    rs.getObject("joined_at", LocalDateTime.class)
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<ChannelMemberDto> findAllByChannelId(Long channelId, Long lastChannelMembershipId, int size) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, user_id, channel_role, joined_at
            FROM channel_memberships
            WHERE channel_id = :channelId
            """);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("channelId", channelId)
                .addValue("size", size);

        if (lastChannelMembershipId != null) {
            sql.append("AND id < :lastChannelMembershipId ");
            params.addValue("lastChannelMembershipId", lastChannelMembershipId);
        }

        sql.append("ORDER BY channel_role, id DESC ");
        sql.append("LIMIT :size");

        return jdbcTemplate.query(
                sql.toString(),
                params,
                channelMembershipDtoRowMapper
        );
    }
}
