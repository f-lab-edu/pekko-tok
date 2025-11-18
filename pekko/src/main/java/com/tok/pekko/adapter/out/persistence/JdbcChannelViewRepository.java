package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.application.port.out.dto.ChannelDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcChannelViewRepository implements ChannelViewRepository {

    private static final RowMapper<ChannelDto> channelDtoRowMapper = (rs, rowNum) -> new ChannelDto(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getInt("membership_count")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public List<ChannelDto> findAll(Long lastChannelId, int size) {
        String sql = """
                SELECT id, name, membership_count
                FROM channels
                WHERE is_deleted = false
                  AND is_public = true
                  AND id < :lastChannelId
                ORDER BY id DESC
                LIMIT :size
                """;
        MapSqlParameterSource parameter = new MapSqlParameterSource()
                .addValue("lastChannelId", lastChannelId)
                .addValue("size", size);

        return namedParameterJdbcTemplate.query(sql, parameter, channelDtoRowMapper);
    }
}
