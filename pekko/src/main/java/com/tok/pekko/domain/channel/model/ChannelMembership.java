package com.tok.pekko.domain.channel.model;

import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelMembershipId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(of = "id")
public class ChannelMembership {

    private final ChannelMembershipId id;
    private final ChannelId channelId;
    private final UserId userId;
    private final ChannelRole role;
    private final ChannelManagePermissions permissions;
    private final LocalDateTime joinedAt;

    public static ChannelMembership create(
            ChannelId channelId,
            UserId userId,
            ChannelRole role,
            LocalDateTime joinedAt
    ) {
        validateChannelId(channelId);
        validateUserId(userId);

        return new ChannelMembership(
                ChannelMembershipId.EMPTY_CHANNEL_MEMBERSHIP_ID,
                channelId,
                userId,
                role,
                createChannelManagePermissions(role),
                joinedAt
        );
    }

    public static ChannelMembership createManager(
            ChannelId channelId,
            UserId userId,
            ChannelManagePermissions permissions,
            LocalDateTime joinedAt
    ) {
        validateUserId(userId);

        return new ChannelMembership(
                ChannelMembershipId.EMPTY_CHANNEL_MEMBERSHIP_ID,
                channelId,
                userId,
                ChannelRole.MANAGER,
                permissions,
                joinedAt
        );
    }

    public static ChannelMembership create(
            Long id,
            Long channelId,
            Long userId,
            ChannelRole role,
            ChannelManagePermissions channelManagePermissions,
            LocalDateTime joinedAt
    ) {
        return new ChannelMembership(
                ChannelMembershipId.create(id),
                ChannelId.create(channelId),
                UserId.create(userId),
                role,
                channelManagePermissions,
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

    private static void validateChannelId(ChannelId channelId) {
        if (channelId == null) {
            throw new IllegalArgumentException("채널 ID는 필수입니다.");
        }
    }

    private static void validateUserId(UserId userId) {
        if (userId == null || userId == UserId.EMPTY_USER_ID) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
    }

    private ChannelMembership(
            ChannelMembershipId id,
            ChannelId channelId,
            UserId userId,
            ChannelRole role,
            ChannelManagePermissions permissions,
            LocalDateTime joinedAt
    ) {
        this.id = id;
        this.channelId = channelId;
        this.userId = userId;
        this.role = role;
        this.permissions = permissions;
        this.joinedAt = joinedAt;
    }

    public ChannelMembership withAssignedId(Long id) {
        return new ChannelMembership(
                ChannelMembershipId.create(id),
                this.channelId,
                this.userId,
                this.role,
                this.permissions,
                this.joinedAt
        );
    }

    public ChannelMembership updatePermissions(ChannelManagePermissions newPermissions) {
        validateRole(this.role);

        return new ChannelMembership(this.id, this.channelId, this.userId, this.role, newPermissions, this.joinedAt);
    }

    public ChannelMembership promoteToManager(ChannelManagePermissions permissions) {
        return new ChannelMembership(
                this.id,
                this.channelId, this.userId, ChannelRole.MANAGER, permissions, this.joinedAt);
    }

    public ChannelMembership demoteToMember() {
        return new ChannelMembership(
                this.id,
                this.channelId,
                this.userId,
                ChannelRole.MEMBER,
                ChannelManagePermissions.ofMember(),
                this.joinedAt
        );
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

    public boolean lacksPermission(ChannelPermissionType permission) {
        return !this.hasPermission(permission);
    }

    public boolean canEditChannelName() {
        if (this.role.isOwner()) {
            return true;
        }

        return this.role.isManager() && permissions.has(ChannelPermissionType.EDIT_CHANNEL_NAME);
    }

    public boolean cannotEditChannelName() {
        return !this.canEditChannelName();
    }

    public boolean canKickMember() {
        if (this.role.isOwner()) {
            return true;
        }

        return this.role.isManager() && permissions.has(ChannelPermissionType.MEMBER_KICK);
    }

    public boolean cannotKickMember() {
        return !this.canKickMember();
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

    public boolean cannotInviteMember() {
        return !this.canInviteMember();
    }

    public boolean canManageRole() {
        return this.role.isOwner();
    }

    public boolean cannotManageRole() {
        return !this.canManageRole();
    }

    public boolean canManagePermission() {
        return this.role.isOwner();
    }

    public boolean cannotManagePermission() {
        return !this.canManagePermission();
    }

    public boolean canDeleteChannel() {
        return this.role.isOwner();
    }

    public boolean cannotDeleteChannel() {
        return !this.canDeleteChannel();
    }

    public boolean canChangeChannelPolicy() {
        return this.role.isOwner();
    }

    public boolean cannotChangeChannelPolicy() {
        return !this.canChangeChannelPolicy();
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
