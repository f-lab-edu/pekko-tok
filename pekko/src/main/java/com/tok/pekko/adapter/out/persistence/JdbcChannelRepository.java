package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcChannelRepository implements ChannelRepository {

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
                    created_at
                )
                VALUES (
                    :name,
                    :creatorId,
                    :canEditOwnMessage,
                    :canDeleteOwnMessage,
                    :isPublic,
                    :isDeleted,
                    :createdAt
                )
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("name", channel.getName())
                .addValue("creatorId", channel.getCreatorId().getValue())
                .addValue("canEditOwnMessage", channel.getChannelPolicy().canEditOwnMessage())
                .addValue("canDeleteOwnMessage", channel.getChannelPolicy().canDeleteOwnMessage())
                .addValue("isPublic", channel.getChannelPolicy().isPublic())
                .addValue("isDeleted", false)
                .addValue("createdAt", channel.getCreatedAt());

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
        if (memberIds == null || memberIds.length == 0) {
            return findById(channelId);
        }

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
                    cm.joined_at AS membership_joined_at,
                    cmp.permission AS manager_permission
                FROM channels c
                INNER JOIN channel_memberships cm ON cm.channel_id = c.id
                LEFT JOIN channel_manager_permissions cmp ON cmp.manager_membership_id = cm.id
                WHERE c.id = :channelId AND c.is_deleted = false AND cm.user_id IN (:memberIds)
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("channelId", channelId)
                .addValue("memberIds", List.of(memberIds));

        Channel channel = jdbcTemplate.query(sql, parameters, new ChannelWithMembershipsExtractor());

        return Optional.ofNullable(channel);
    }

    @Override
    public Optional<Channel> findById(Long channelId) {
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
                    cm.joined_at AS membership_joined_at,
                    cmp.permission AS manager_permission
                FROM channels c
                LEFT JOIN channel_memberships cm ON c.id = cm.channel_id
                LEFT JOIN channel_manager_permissions cmp ON cmp.manager_membership_id = cm.id
                WHERE c.id = :channelId AND c.is_deleted = false
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("channelId", channelId);

        Channel channel = jdbcTemplate.query(sql, parameters, new ChannelWithMembershipsExtractor());

        return Optional.ofNullable(channel);
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

    private static class ChannelWithMembershipsExtractor implements ResultSetExtractor<Channel> {

        @Override
        public Channel extractData(ResultSet rs) throws SQLException, DataAccessException {
            if (!rs.next()) {
                return null;
            }

            Long channelId = rs.getLong("channel_id");
            String name = rs.getString("name");
            Long creatorId = rs.getLong("creator_id");
            ChannelPolicy channelPolicy = new ChannelPolicy(
                    rs.getBoolean("can_edit_own_message"),
                    rs.getBoolean("can_delete_own_message"),
                    rs.getBoolean("is_public")
            );
            LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
            Map<UserId, ChannelMembership> memberships = new HashMap<>();

            Map<Long, EnumSet<ChannelPermissionType>> managerPermissions = new HashMap<>();

            do {
                Long membershipId = rs.getLong("membership_id");
                if (rs.wasNull()) {
                    continue;
                }

                Long userIdValue = rs.getLong("membership_user_id");
                UserId userId = UserId.create(userIdValue);
                ChannelRole role = ChannelRole.find(rs.getString("channel_role"));
                LocalDateTime joinedAt = rs.getTimestamp("membership_joined_at").toLocalDateTime();

                String managerPermission = rs.getString("manager_permission");
                if (managerPermission != null) {
                    managerPermissions.computeIfAbsent(membershipId, ignored -> EnumSet.noneOf(ChannelPermissionType.class))
                                .add(ChannelPermissionType.valueOf(managerPermission));
                }

                ChannelMembership membership = createMembership(
                        ChannelId.create(channelId),
                        membershipId,
                        userId,
                        role,
                        joinedAt,
                        managerPermissions.get(membershipId)
                );
                memberships.put(userId, membership);
            } while (rs.next());

            return Channel.create(channelId, name, creatorId, channelPolicy, memberships, createdAt);
        }

        private ChannelMembership createMembership(
                ChannelId channelId,
                Long membershipId,
                UserId userId,
                ChannelRole role,
                LocalDateTime joinedAt,
                EnumSet<ChannelPermissionType> permissions
        ) {
            if (role.isManager() && permissions != null && !permissions.isEmpty()) {
                return ChannelMembership.create(
                        membershipId,
                        channelId.getValue(),
                        userId.getValue(),
                        role,
                        ChannelManagePermissions.ofManager(permissions),
                        joinedAt
                );
            }

            return ChannelMembership.create(channelId, userId, role, joinedAt)
                                    .withAssignedId(membershipId);
        }
    }
}
