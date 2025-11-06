package com.tok.pekko.domain.channel.model;

import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelMembershipId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(of = "id")
public class ChannelMembership {

    private final ChannelMembershipId id;
    private final UserId userId;
    private final ChannelRole role;
    private final ChannelManagePermissions permissions;
    private final LocalDateTime joinedAt;

    public static ChannelMembership create(UserId userId, ChannelRole role, LocalDateTime joinedAt) {
        validateUserId(userId);

        return new ChannelMembership(
                ChannelMembershipId.EMPTY_CHANNEL_MEMBERSHIP_ID,
                userId,
                role,
                createChannelManagePermissions(role),
                joinedAt
        );
    }

    public static ChannelMembership createManager(UserId userId, ChannelManagePermissions permissions, LocalDateTime joinedAt) {
        validateUserId(userId);

        return new ChannelMembership(
                ChannelMembershipId.EMPTY_CHANNEL_MEMBERSHIP_ID,
                userId,
                ChannelRole.MANAGER,
                permissions,
                joinedAt
        );
    }

    private static ChannelManagePermissions createChannelManagePermissions(ChannelRole role) {
        if (role.isMember()) {
            return ChannelManagePermissions.ofMember();
        }
        if (role.isOwner()) {
            return ChannelManagePermissions.ofOwner();
        }

        return ChannelManagePermissions.ofManager();
    }

    public static ChannelMembership create(
            Long id,
            Long userId,
            ChannelRole role,
            ChannelManagePermissions channelManagePermissions,
            LocalDateTime joinedAt
    ) {
        return new ChannelMembership(
                ChannelMembershipId.create(id),
                UserId.create(userId),
                role,
                channelManagePermissions,
                joinedAt
        );
    }

    private static void validateUserId(UserId userId) {
        if (userId == null || userId == UserId.EMPTY_USER_ID) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
    }

    private ChannelMembership(
            ChannelMembershipId id,
            UserId userId,
            ChannelRole role,
            ChannelManagePermissions permissions,
            LocalDateTime joinedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.permissions = permissions;
        this.joinedAt = joinedAt;
    }

    public ChannelMembership withAssignedId(Long id) {
        return new ChannelMembership(
                ChannelMembershipId.create(id),
                this.userId,
                this.role,
                this.permissions,
                this.joinedAt
        );
    }

    public ChannelMembership updatePermissions(ChannelManagePermissions newPermissions) {
        validateRole(this.role);

        return new ChannelMembership(this.id, this.userId, this.role, newPermissions, this.joinedAt);
    }

    public ChannelMembership promoteToManager(ChannelManagePermissions permissions) {
        return new ChannelMembership(this.id, this.userId, ChannelRole.MANAGER, permissions, this.joinedAt);
    }

    public ChannelMembership demoteToMember() {
        return new ChannelMembership(
                this.id,
                this.userId,
                ChannelRole.MEMBER,
                ChannelManagePermissions.ofMember(),
                this.joinedAt
        );
    }

    public ChannelMembership addPermission(ChannelPermissionType permission) {
        validateRole(this.role);

        return new ChannelMembership(this.id, this.userId, this.role, this.permissions.add(permission), this.joinedAt);
    }

    public ChannelMembership removePermission(ChannelPermissionType permission) {
        validateRole(this.role);

        return new ChannelMembership(
                this.id,
                this.userId,
                this.role,
                this.permissions.remove(permission),
                this.joinedAt
        );
    }

    public ChannelMembership changeRole(ChannelRole newRole, Set<ChannelPermissionType> newPermissions) {
        return new ChannelMembership(
                this.id,
                this.userId,
                newRole,
                createChannelManagePermissions(newRole, newPermissions),
                this.joinedAt
        );
    }

    private ChannelManagePermissions createChannelManagePermissions(
            ChannelRole newRole,
            Set<ChannelPermissionType> newPermissions
    ) {
        if (newRole.isMember()) {
            return ChannelManagePermissions.ofMember();
        }
        if (newRole.isOwner()) {
            return ChannelManagePermissions.ofOwner();
        }
        if (newPermissions.isEmpty()) {
            return ChannelManagePermissions.ofManager();
        }

        return ChannelManagePermissions.ofManager(newPermissions);
    }

    public boolean hasPermission(ChannelPermissionType permission) {
        if (this.role.isOwner()) {
            return true;
        }
        if (this.role.isManager()) {
            return permissions.has(permission);
        }

        return false;
    }

    public boolean canEditChannelName() {
        if (this.role.isOwner()) {
            return true;
        }

        return this.role.isManager() && permissions.has(ChannelPermissionType.EDIT_CHANNEL_NAME);
    }

    public boolean canKickMember() {
        if (this.role.isOwner()) {
            return true;
        }

        return this.role.isManager() && permissions.has(ChannelPermissionType.MEMBER_KICK);
    }

    public boolean canDeleteMessage(ChannelPolicy policy) {
        if (this.role.isOwner()) {
            return true;
        }
        if (this.role.isManager()) {
            return permissions.has(ChannelPermissionType.MESSAGE_DELETE);
        }
        if (this.role.isMember()) {
            return policy.canDeleteOwnMessage();
        }

        return false;
    }

    public boolean canEditMessage(ChannelPolicy policy) {
        if (this.role.isOwner()) {
            return true;
        }
        if (this.role.isManager()) {
            return permissions.has(ChannelPermissionType.MESSAGE_EDIT);
        }
        if (this.role.isMember()) {
            return policy.canEditOwnMessage();
        }

        return false;
    }

    public boolean canInviteMember() {
        if (this.role.isOwner()) {
            return true;
        }

        return this.role.isManager() && permissions.has(ChannelPermissionType.MEMBER_INVITE);
    }

    public boolean canMemberRoleManagement() {
        return this.role.isOwner();
    }

    public boolean canPermissionManagement() {
        return this.role.isOwner();
    }

    public boolean canDeleteChannel() {
        return this.role.isOwner();
    }

    public boolean canChangeChannelPolicy() {
        return this.role.isOwner();
    }

    public boolean isManager() {
        return this.role.isManager();
    }

    public boolean isMember() {
        return this.role.isMember();
    }

    public boolean isOwner() {
        return this.role.isOwner();
    }

    private void validateRole(ChannelRole role) {
        if (role.isOwner() || role.isMember()) {
            throw new IllegalArgumentException("매니저가 아니라면 채널 권한을 관리할 수 없습니다.");
        }
    }
}
