package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.user.model.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcChannelMembershipRepository implements ChannelMembershipRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void joinChannel(ChannelMembership channelMembership) {
        String sql = """
                INSERT INTO channel_memberships (channel_id, user_id, channel_role, joined_at)
                VALUES (:channelId, :userId, :channelRole, :joinedAt)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("channelId", channelMembership.getChannelId().getValue())
                .addValue("userId", channelMembership.getUserId().getValue())
                .addValue("channelRole", channelMembership.getRole().name())
                .addValue("joinedAt", channelMembership.getJoinedAt());

        jdbcTemplate.update(sql, params);
    }

    @Override
    public void leaveChannel(ChannelId channelId, UserId userId) {
        String sql = """
                DELETE FROM channel_memberships
                WHERE channel_id = :channelId AND user_id = :userId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("channelId", channelId.getValue())
                .addValue("userId", userId.getValue());

        jdbcTemplate.update(sql, params);
    }

    @Override
    public void updateRole(ChannelMembership channelMembership) {
        String sql = """
                UPDATE channel_memberships
                SET channel_role = :role
                WHERE id = :membershipId
                """;
        MapSqlParameterSource parameter = new MapSqlParameterSource()
                .addValue("role", channelMembership.getRole().name())
                .addValue("membershipId", channelMembership.getId().getValue());

        jdbcTemplate.update(sql, parameter);
    }

    @Override
    public void delete(ChannelId channelId, UserId userId) {
        String sql = """
                DELETE FROM channel_memberships
                WHERE channel_id = :channelId
                AND user_id = :userId
                """;
        MapSqlParameterSource parameter = new MapSqlParameterSource()
                .addValue("channelId", channelId.getValue())
                .addValue("userId", userId.getValue());

        jdbcTemplate.update(sql, parameter);
    }
}

