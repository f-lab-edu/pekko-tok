package com.tok.pekko.domain.channel.model;

import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(of = "channelId")
public class Channel {

    private final ChannelId channelId;
    private final String name;
    private final UserId creatorId;
    private final ChannelPolicy channelPolicy;
    private final Map<UserId, ChannelMembership> memberships;
    private final LocalDateTime createdAt;

    public static Channel create(String name, Long creatorId, ChannelPolicy channelPolicy, LocalDateTime createdAt) {
        validateName(name);

        return new Channel(
                ChannelId.EMPTY_CHANNEL_ID,
                name,
                UserId.create(creatorId),
                channelPolicy,
                new HashMap<>(),
                createdAt
        );
    }

    public static Channel create(
            Long channelId,
            String name,
            Long creatorId,
            ChannelPolicy channelPolicy,
            Map<UserId, ChannelMembership> memberships,
            LocalDateTime createdAt
    ) {
        validateName(name);

        return new Channel(
                ChannelId.create(channelId),
                name,
                UserId.create(creatorId),
                channelPolicy,
                memberships,
                createdAt
        );
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("채널 이름은 필수입니다.");
        }
    }

    private Channel(
            ChannelId channelId,
            String name,
            UserId creatorId,
            ChannelPolicy channelPolicy,
            Map<UserId, ChannelMembership> memberships,
            LocalDateTime createdAt
    ) {
        this.channelId = channelId;
        this.name = name;
        this.creatorId = creatorId;
        this.channelPolicy = channelPolicy;
        this.memberships = memberships;
        this.createdAt = createdAt;
    }

    public Channel withAssignedId(Long id) {
        return new Channel(
                ChannelId.create(id),
                this.name,
                this.creatorId,
                this.channelPolicy,
                this.memberships,
                this.createdAt
        );
    }

    public void validateJoinMember(UserId userId) {
        if (!channelPolicy.isPublic()) {
            throw new IllegalArgumentException("비공개 채널입니다. 초대를 통해 참여할 수 있습니다.");
        }
        if (memberships.containsKey(userId)) {
            throw new IllegalArgumentException("이미 채널에 참여한 사용자입니다.");
        }
    }

    public void validateInviteMember(UserId inviterId, UserId inviteeId) {
        if (memberships.containsKey(inviteeId)) {
            throw new IllegalArgumentException("이미 채널에 참여한 사용자입니다.");
        }

        ChannelMembership inviterMembership = memberships.get(inviterId);

        if (inviterMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!inviterMembership.canInviteMember()) {
            throw new ChannelMembershipOperationForbiddenException("멤버를 초대할 권한이 없습니다.");
        }
    }

    public ChannelMembership promoteToManager(UserId executorId, UserId targetUserId) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        if (!executorMembership.canMemberRoleManagement()) {
            throw new ChannelMembershipOperationForbiddenException("멤버의 역할을 변경할 권한이 없습니다.");
        }

        ChannelMembership targetUserMembership = memberships.get(targetUserId);

        if (targetUserMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        if (targetUserMembership.isManager()) {
            throw new IllegalArgumentException("이미 매니저입니다.");
        }
        if (targetUserMembership.isOwner()) {
            throw new IllegalArgumentException("오너를 매니저로 강등시킬 수 없습니다.");
        }

        return targetUserMembership.promoteToManager(ChannelManagePermissions.ofManager());
    }

    public ChannelMembership demoteToMember(UserId executorId, UserId targetUserId) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        if (!executorMembership.canMemberRoleManagement()) {
            throw new ChannelMembershipOperationForbiddenException("멤버의 역할을 변경할 권한이 없습니다.");
        }

        ChannelMembership targetUserMembership = memberships.get(targetUserId);

        if (targetUserMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (targetUserMembership.isMember()) {
            throw new IllegalArgumentException("이미 멤버입니다.");
        }
        if (targetUserMembership.isOwner()) {
            throw new IllegalArgumentException("오너를 멤버로 강등시킬 수 없습니다.");
        }

        return targetUserMembership.demoteToMember();
    }

    public ChannelMembership getValidatedAddTarget(UserId grantorId, UserId granteeId, ChannelPermissionType permission) {
        ChannelMembership grantorMembership = memberships.get(grantorId);

        if (grantorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        if (!grantorMembership.canPermissionManagement()) {
            throw new ChannelMembershipOperationForbiddenException("매니저의 권한을 추가할 권한이 없습니다.");
        }

        ChannelMembership granteeMembership = memberships.get(granteeId);

        if (granteeMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!granteeMembership.isManager()) {
            throw new IllegalArgumentException("해당 멤버는 매니저가 아닙니다.");
        }
        if (granteeMembership.hasPermission(permission)) {
            throw new IllegalArgumentException("이미 해당 권한을 가지고 있습니다.");
        }

        return granteeMembership;
    }

    public ChannelMembership getValidatedRemoveTarget(UserId grantorId, UserId granteeId, ChannelPermissionType permission) {
        ChannelMembership grantorMembership = memberships.get(grantorId);

        if (grantorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        if (!grantorMembership.canPermissionManagement()) {
            throw new ChannelMembershipOperationForbiddenException("매니저의 권한을 추가할 권한이 없습니다.");
        }

        ChannelMembership granteeMembership = memberships.get(granteeId);

        if (granteeMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!granteeMembership.isManager()) {
            throw new IllegalArgumentException("해당 멤버는 매니저가 아닙니다.");
        }
        if (!granteeMembership.hasPermission(permission)) {
            throw new IllegalArgumentException("해당 권한을 가지고 있지 않습니다.");
        }

        return granteeMembership;
    }

    public ChannelMembership getValidatedLeaveMember(UserId userId) {
        ChannelMembership leaveMembership = memberships.get(userId);

        if (leaveMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (leaveMembership.isOwner()) {
            throw new IllegalArgumentException("오너는 채널에서 나갈 수 없습니다.");
        }

        return leaveMembership;
    }

    public void validateDeleteChannel(UserId deleterId) {
        ChannelMembership deleterMembership = memberships.get(deleterId);

        if (deleterMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!deleterMembership.canDeleteChannel()) {
            throw new ChannelOperationForbiddenException("채널을 삭제할 권한이 없습니다.");
        }
    }

    public ChannelMembership getValidatedKickedMember(UserId executorId, UserId targetUserId) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!executorMembership.canKickMember()) {
            throw new IllegalArgumentException("멤버를 강퇴할 권한이 없습니다.");
        }

        if (executorId.equals(targetUserId)) {
            throw new IllegalArgumentException("자기 자신을 강퇴할 수 없습니다.");
        }

        ChannelMembership targetUserMembership = memberships.get(targetUserId);

        if (targetUserMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        if (targetUserMembership.isOwner()) {
            throw new IllegalArgumentException("오너를 강퇴할 수 없습니다.");
        }

        return targetUserMembership;
    }

    public Channel changeName(UserId changerId, String newName) {
        ChannelMembership changerMembership = memberships.get(changerId);

        if (changerMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!changerMembership.canEditChannelName()) {
            throw new ChannelOperationForbiddenException("채널 이름을 변경할 권한이 없습니다.");
        }

        validateName(newName);

        return new Channel(
                this.channelId,
                newName,
                this.creatorId,
                this.channelPolicy,
                this.memberships,
                this.createdAt
        );
    }

    public Channel changeChannelPolicy(UserId userId, ChannelPolicy channelPolicy) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!channelMembership.canChangeChannelPolicy()) {
            throw new ChannelOperationForbiddenException("채널 정책을 변경할 권한이 없습니다.");
        }

        return new Channel(
                this.channelId,
                this.name,
                this.creatorId,
                channelPolicy,
                this.memberships,
                this.createdAt
        );
    }

    public boolean isPublic() {
        return channelPolicy.isPublic();
    }

    public boolean isPrivate() {
        return !isPublic();
    }

    public static class ChannelMembershipNotFoundException extends IllegalArgumentException {

        public ChannelMembershipNotFoundException() {
            super("채널 멤버를 찾을 수 없습니다.");
        }
    }

    public static class ChannelOperationForbiddenException extends IllegalArgumentException {

        public ChannelOperationForbiddenException(String s) {
            super(s);
        }
    }

    public static class ChannelMembershipOperationForbiddenException extends IllegalArgumentException {

        public ChannelMembershipOperationForbiddenException(String s) {
            super(s);
        }
    }
}
