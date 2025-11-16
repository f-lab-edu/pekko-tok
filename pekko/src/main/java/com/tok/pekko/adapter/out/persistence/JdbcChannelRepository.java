package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcChannelRepository implements ChannelRepository {

    private static final RowMapper<Channel> channelRowMapper = (rs, rowNum) -> {
        ChannelPolicy channelPolicy = new ChannelPolicy(
                rs.getBoolean("can_edit_own_message"),
                rs.getBoolean("can_delete_own_message"),
                rs.getBoolean("is_public")
        );
        LocalDateTime createdAt = rs.getObject("created_at", LocalDateTime.class);
        ChannelRole role = ChannelRole.find(rs.getString("channel_role"));
        UserId membershipUserId = UserId.create(rs.getLong("membership_user_id"));
        LocalDateTime membershipJoinedAt = rs.getObject("membership_joined_at", LocalDateTime.class);
        Long channelId = rs.getLong("channel_id");
        ChannelMembership membership = ChannelMembership.create(ChannelId.create(channelId), membershipUserId, role, membershipJoinedAt)
                                                        .withAssignedId(rs.getLong("membership_id"));
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        memberships.put(membership.getUserId(), membership);

        return Channel.create(
                channelId,
                rs.getString("name"),
                rs.getLong("creator_id"),
                channelPolicy,
                memberships,
                createdAt
        );
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public Channel save(Channel channel) {
        String sql = """
                INSERT INTO channels (
                    name,
                    creator_id,
                    can_edit_own_message,
                    can_delete_own_message,
                    is_public,
                    is_deleted,
                    created_at,
                    membership_count
                )
                VALUES (
                    :name,
                    :creatorId,
                    :canEditOwnMessage,
                    :canDeleteOwnMessage,
                    :isPublic,
                    :isDeleted,
                    :createdAt,
                    :membershipCount
                )
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("name", channel.getName())
                .addValue("creatorId", channel.getCreatorId().getValue())
                .addValue("canEditOwnMessage", channel.getChannelPolicy().canEditOwnMessage())
                .addValue("canDeleteOwnMessage", channel.getChannelPolicy().canDeleteOwnMessage())
                .addValue("isPublic", channel.getChannelPolicy().isPublic())
                .addValue("isDeleted", false)
                .addValue("createdAt", channel.getCreatedAt())
                .addValue("membershipCount", channel.getMemberships().size());
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(sql, parameters, keyHolder, new String[]{"id"});

        Number generatedId = keyHolder.getKey();

        if (generatedId == null) {
            throw new IllegalStateException("채널 ID를 생성하지 못했습니다.");
        }

        return Channel.create(
                generatedId.longValue(),
                channel.getName(),
                channel.getCreatorId().getValue(),
                channel.getChannelPolicy(),
                channel.getMemberships(),
                channel.getCreatedAt()
        );
    }

    @Override
    public Optional<Channel> findByIdWithMembership(Long channelId, Long... memberIds) {
        String sql = """
                SELECT
                    c.id AS channel_id,
                    c.name,
                    c.creator_id,
                    c.can_edit_own_message,
                    c.can_delete_own_message,
                    c.is_public,
                    c.created_at,
                    cm.id AS membership_id,
                    cm.user_id AS membership_user_id,
                    cm.channel_role,
                    cm.joined_at AS membership_joined_at
                FROM channels c
                INNER JOIN channel_memberships cm ON cm.channel_id = c.id
                WHERE c.id = :channelId AND c.is_deleted = false AND cm.user_id IN (:memberIds)
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("channelId", channelId)
                .addValue("memberIds", List.of(memberIds));

        try {
            Channel channel = jdbcTemplate.queryForObject(sql, parameters, channelRowMapper);

            return Optional.ofNullable(channel);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void update(Channel channel) {
        String sql = """
                UPDATE channels
                SET
                    name = :name,
                    can_edit_own_message = :canEditOwnMessage,
                    can_delete_own_message = :canDeleteOwnMessage,
                    is_public = :isPublic
                WHERE id = :channelId AND is_deleted = false
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("channelId", channel.getChannelId().getValue())
                .addValue("name", channel.getName())
                .addValue("canEditOwnMessage", channel.getChannelPolicy().canEditOwnMessage())
                .addValue("canDeleteOwnMessage", channel.getChannelPolicy().canDeleteOwnMessage())
                .addValue("isPublic", channel.getChannelPolicy().isPublic());

        jdbcTemplate.update(sql, parameters);
    }

    @Override
    @Transactional
    public void deleteById(ChannelId channelId) {
        String sql = """
                UPDATE channels
                SET is_deleted = true
                WHERE id = :channelId
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource("channelId", channelId.getValue());

        jdbcTemplate.update(sql, parameters);
    }

    @Override
    @Transactional
    public void incrementMemberCount(ChannelId channelId) {
        String sql = """
                UPDATE channels
                SET membership_count = membership_count + 1
                WHERE id = :channelId AND is_deleted = false
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource("channelId", channelId.getValue());

        jdbcTemplate.update(sql, parameters);
    }

    @Override
    @Transactional
    public void decrementMemberCount(ChannelId channelId) {
        String sql = """
                UPDATE channels
                SET membership_count = membership_count - 1
                WHERE id = :channelId AND is_deleted = false
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource("channelId", channelId.getValue());

        jdbcTemplate.update(sql, parameters);
    }
}
