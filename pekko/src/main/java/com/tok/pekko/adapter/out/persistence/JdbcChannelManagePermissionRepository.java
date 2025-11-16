package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelMembershipId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcChannelManagePermissionRepository implements ChannelManagePermissionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void save(ChannelMembership channelMembership, ChannelPermissionType permission) {
        String sql = """
                INSERT INTO channel_manager_permissions (manager_membership_id, permission)
                VALUES (:membershipId, :permission)
                """;
        MapSqlParameterSource parameter = new MapSqlParameterSource()
                .addValue("membershipId", channelMembership.getId().getValue())
                .addValue("permission", permission.name());

        jdbcTemplate.update(sql, parameter);
    }

    @Override
    public void saveAll(ChannelMembership channelMembership) {
        String sql = """
                INSERT INTO channel_manager_permissions (manager_membership_id, permission)
                VALUES (:membershipId, :permission)
                """;
        SqlParameterSource[] batchParameter = channelMembership.getPermissions()
                           .getAll()
                           .stream()
                           .map(
                                   permission -> new MapSqlParameterSource()
                                           .addValue("membershipId", channelMembership.getId().getValue())
                                           .addValue("permission", permission.name())
                           )
                           .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, batchParameter);
    }

    @Override
    public void delete(ChannelMembership channelMembership, ChannelPermissionType permission) {
        String sql = """
                DELETE FROM channel_manager_permissions
                WHERE manager_membership_id = :membershipId
                AND permission = :permission
                """;
        MapSqlParameterSource parameter = new MapSqlParameterSource()
                .addValue("membershipId", channelMembership.getId().getValue())
                .addValue("permission", permission.name());

        jdbcTemplate.update(sql, parameter);
    }

    @Override
    public void deleteAll(ChannelMembershipId channelMembershipId) {
        String sql = """
                DELETE FROM channel_manager_permissions
                WHERE manager_membership_id = :membershipId
                """;
        MapSqlParameterSource parameter = new MapSqlParameterSource()
                .addValue("membershipId", channelMembershipId.getValue());

        jdbcTemplate.update(sql, parameter);
    }
}
