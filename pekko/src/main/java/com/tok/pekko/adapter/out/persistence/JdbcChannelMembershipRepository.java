package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelMembershipId;
import com.tok.pekko.domain.user.model.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcChannelMembershipRepository implements ChannelMembershipRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public ChannelMembership joinChannel(ChannelMembership channelMembership) {
        String sql = """
                INSERT INTO channel_memberships (channel_id, user_id, channel_role, joined_at)
                VALUES (:channelId, :userId, :channelRole, :joinedAt)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("channelId", channelMembership.getChannelId().getValue())
                .addValue("userId", channelMembership.getUserId().getValue())
                .addValue("channelRole", channelMembership.getRole().name())
                .addValue("joinedAt", channelMembership.getJoinedAt());

        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("채널 멤버십 ID를 생성하지 못했습니다.");
        }

        return channelMembership.withAssignedId(generatedId.longValue());
    }

    @Override
    @Transactional
    public void leaveChannel(ChannelMembership channelMembership) {
        // 매니저 권한 테이블을 먼저 정리한 뒤 멤버십을 삭제한다.
        String deletePermSql = """
                DELETE FROM channel_manager_permissions
                WHERE manager_membership_id = :membershipId
                """;
        MapSqlParameterSource deletePermParams = new MapSqlParameterSource()
                .addValue("membershipId", channelMembership.getId().getValue());
        jdbcTemplate.update(deletePermSql, deletePermParams);

        String deleteMembershipSql = """
                DELETE FROM channel_memberships
                WHERE id = :membershipId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("membershipId", channelMembership.getId().getValue());

        jdbcTemplate.update(deleteMembershipSql, params);
    }

    @Override
    @Transactional
    public void promoteToManager(ChannelMembership channelMembership) {
        String roleSql = """
                UPDATE channel_memberships
                SET channel_role = 'MANAGER'
                WHERE id = :membershipId
                """;
        MapSqlParameterSource roleParams = new MapSqlParameterSource()
                .addValue("membershipId", channelMembership.getId().getValue());
        jdbcTemplate.update(roleSql, roleParams);

        String deletePermSql = """
                DELETE FROM channel_manager_permissions
                WHERE manager_membership_id = :membershipId
                """;
        MapSqlParameterSource deleteParams = new MapSqlParameterSource()
                .addValue("membershipId", channelMembership.getId().getValue());
        jdbcTemplate.update(deletePermSql, deleteParams);

        var permissions = channelMembership.getPermissions().getAll();
        if (permissions != null && !permissions.isEmpty()) {
            String insertPermSql = """
                    INSERT INTO channel_manager_permissions (manager_membership_id, permission)
                    VALUES (:membershipId, :permission)
                    """;
            SqlParameterSource[] batchParams = permissions.stream()
                                                          .map(permission -> new MapSqlParameterSource()
                                                                  .addValue("membershipId", channelMembership.getId().getValue())
                                                                  .addValue("permission", permission.name()))
                                                          .toArray(SqlParameterSource[]::new);
            jdbcTemplate.batchUpdate(insertPermSql, batchParams);
        }
    }

    @Override
    @Transactional
    public void demoteToMember(ChannelMembership channelMembership) {
        String roleSql = """
                UPDATE channel_memberships
                SET channel_role = 'MEMBER'
                WHERE id = :membershipId
                """;
        MapSqlParameterSource roleParams = new MapSqlParameterSource()
                .addValue("membershipId", channelMembership.getId().getValue());
        jdbcTemplate.update(roleSql, roleParams);

        String deletePermSql = """
                DELETE FROM channel_manager_permissions
                WHERE manager_membership_id = :membershipId
                """;
        MapSqlParameterSource deleteParams = new MapSqlParameterSource()
                .addValue("membershipId", channelMembership.getId().getValue());
        jdbcTemplate.update(deletePermSql, deleteParams);
    }

    @Override
    @Transactional
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

    @Override
    @Transactional
    public void addManagerPermission(ChannelMembershipId membershipId, ChannelPermissionType permission) {
        if (permission == null) {
            return;
        }
        String sql = """
                INSERT INTO channel_manager_permissions (manager_membership_id, permission)
                VALUES (:membershipId, :permission)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("membershipId", membershipId.getValue())
                .addValue("permission", permission.name());
        jdbcTemplate.update(sql, params);
    }

    @Override
    @Transactional
    public void deleteManagerPermission(ChannelMembershipId membershipId, ChannelPermissionType permission) {
        if (permission == null) {
            return;
        }
        String sql = """
                DELETE FROM channel_manager_permissions
                WHERE manager_membership_id = :membershipId
                AND permission = :permission
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("membershipId", membershipId.getValue())
                .addValue("permission", permission.name());
        jdbcTemplate.update(sql, params);
    }
}
