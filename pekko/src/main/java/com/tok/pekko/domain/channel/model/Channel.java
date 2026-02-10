package com.tok.pekko.domain.channel.model;

import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(of = "channelId")
public class Channel {

    private ChannelId channelId;
    private String name;
    private ChannelPolicy channelPolicy;
    private final UserId creatorId;
    private final LocalDateTime createdAt;
    private final Map<UserId, ChannelMembership> memberships;

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
        this.channelId = ChannelId.create(id);
        return this;
    }

    public ChannelMembership joinUser(UserId userId, ChannelRole role, LocalDateTime joinedAt) {
        validateJoinUser(userId);

        ChannelMembership joinerMembership = ChannelMembership.create(this.channelId, userId, role, joinedAt);

        memberships.put(userId, joinerMembership);
        return joinerMembership;
    }

    public ChannelMembership inviteMember(UserId inviterId, UserId inviteeId, LocalDateTime invitedAt) {
        validateInviteMember(inviterId, inviteeId);

        ChannelMembership inviteeMembership = ChannelMembership.create(
                this.channelId,
                inviteeId,
                ChannelRole.MEMBER,
                invitedAt
        );

        memberships.put(inviteeId, inviteeMembership);
        return inviteeMembership;
    }

    public ChannelMembership leaveMember(UserId memberId) {
        validateLeaveMember(memberId);

        return memberships.remove(memberId);
    }

    public ChannelMembership kickMember(UserId executorId, UserId targetUserId) {
        validateKickMember(executorId, targetUserId);

        return memberships.remove(targetUserId);
    }

    public ChannelMembership addPermission(UserId grantorId, UserId granteeId, ChannelPermissionType permission) {
        validateAddPermission(grantorId, granteeId, permission);

        ChannelMembership granteeMembership = getMembership(granteeId);

        ChannelMembership addedPermissionMembership = granteeMembership.addPermission(permission);

        memberships.put(granteeId, addedPermissionMembership);

        return addedPermissionMembership;
    }

    public ChannelMembership removePermission(UserId grantorId, UserId granteeId, ChannelPermissionType permission) {
        validateRemovePermission(grantorId, granteeId, permission);

        ChannelMembership granteeMembership = getMembership(granteeId);

        ChannelMembership removedPermissionMembership = granteeMembership.removePermission(permission);

        memberships.put(granteeId, removedPermissionMembership);

        return removedPermissionMembership;
    }

    public ChannelMembership promoteToManager(UserId executorId, UserId targetUserId) {
        validatePromoteToManager(executorId, targetUserId);

        ChannelMembership targetUserMembership = getMembership(targetUserId);

        return targetUserMembership.promoteToManager(ChannelManagePermissions.ofManager());
    }

    public ChannelMembership demoteToMember(UserId executorId, UserId targetUserId) {
        validateDemoteToMember(executorId, targetUserId);

        ChannelMembership targetUserMembership = getMembership(targetUserId);

        return targetUserMembership.demoteToMember();
    }

    public Channel editName(UserId changerId, String newName) {
        validateEditName(changerId, newName);

        this.name = newName;
        return this;
    }

    public Channel changeChannelPolicy(UserId changerId, ChannelPolicy channelPolicy) {
        validateChangeChannelPolicy(changerId);

        this.channelPolicy = channelPolicy;
        return this;
    }

    public void validateDeleteChannel(UserId deleterId) {
        validateDeleteChannelPermission(deleterId);
    }

    public void validateMemberEditMessage(UserId executorId, ChatMessage message) {
        validateMemberEditMessage(executorId);

        ChannelMembership executorMembership = getMembership(executorId);
        validateMemberEditMessage(executorMembership, executorId, message);
    }

    public void validateMemberDeleteMessage(UserId executorId, ChatMessage message) {
        validateMemberDeleteMessage(executorId);

        ChannelMembership executorMembership = getMembership(executorId);
        validateMemberDeleteMessage(executorMembership, executorId, message);
    }

    public void validateMemberSendMessage(UserId senderId) {
        validateMemberSendMessageMembership(senderId);
    }

    private ChannelMembership getMembership(UserId userId) {
        return memberships.get(userId);
    }

    private void validateMembershipExists(UserId userId) {
        ChannelMembership membership = memberships.get(userId);

        if (membership == null) {
            throw new ChannelMembershipNotFoundException();
        }
    }

    private void validateJoinUser(UserId userId) {
        if (channelPolicy.isPrivate()) {
            throw new ChannelMembershipOperationForbiddenException("비공개 채널입니다. 초대를 통해 참여할 수 있습니다.");
        }
        if (memberships.containsKey(userId)) {
            throw new IllegalArgumentException("이미 채널에 참여한 사용자입니다.");
        }
    }

    private void validateInviteMember(UserId inviterId, UserId inviteeId) {
        validateMembershipExists(inviterId);
        ChannelMembership inviterMembership = getMembership(inviterId);

        if (inviterMembership.cannotInviteMember()) {
            throw new ChannelMembershipOperationForbiddenException("멤버 초대 권한이 없습니다.");
        }
        if (memberships.containsKey(inviteeId)) {
            throw new IllegalArgumentException("이미 해당 채널에 참여한 사용자입니다.");
        }
    }

    private void validateLeaveMember(UserId memberId) {
        validateMembershipExists(memberId);
        ChannelMembership channelMembership = getMembership(memberId);

        if (channelMembership.isOwner()) {
            throw new IllegalArgumentException("채널 오너는 채널에서 나갈 수 없습니다.");
        }
    }

    private void validateKickMember(UserId executorId, UserId targetUserId) {
        validateMembershipExists(executorId);
        ChannelMembership executorMembership = getMembership(executorId);

        if (!executorMembership.canKickMember()) {
            throw new ChannelMembershipOperationForbiddenException("멤버를 강퇴할 권한이 없습니다.");
        }
        validateMembershipExists(targetUserId);
        ChannelMembership targetUserMembership = getMembership(targetUserId);

        if (targetUserMembership.isNotMember()) {
            throw new IllegalArgumentException("멤버만 강퇴할 수 있습니다.");
        }
    }

    private void validateAddPermission(UserId grantorId, UserId granteeId, ChannelPermissionType permission) {
        validateMembershipExists(grantorId);

        ChannelMembership grantorMembership = getMembership(grantorId);

        if (grantorMembership.cannotManagePermission()) {
            throw new ChannelMembershipOperationForbiddenException("매니저의 권한을 추가할 권한이 없습니다.");
        }

        validateMembershipExists(granteeId);

        ChannelMembership granteeMembership = getMembership(granteeId);

        if (granteeMembership.isNotManager()) {
            throw new IllegalArgumentException("해당 멤버는 매니저가 아닙니다.");
        }
        if (granteeMembership.hasPermission(permission)) {
            throw new IllegalArgumentException("이미 해당 권한을 가지고 있습니다.");
        }
    }

    private void validateRemovePermission(UserId grantorId, UserId granteeId, ChannelPermissionType permission) {
        validateMembershipExists(grantorId);

        ChannelMembership grantorMembership = getMembership(grantorId);

        if (grantorMembership.cannotManagePermission()) {
            throw new ChannelMembershipOperationForbiddenException("매니저의 권한을 추가할 권한이 없습니다.");
        }

        validateMembershipExists(granteeId);

        ChannelMembership granteeMembership = getMembership(granteeId);

        if (granteeMembership.isNotManager()) {
            throw new IllegalArgumentException("해당 멤버는 매니저가 아닙니다.");
        }
        if (granteeMembership.lacksPermission(permission)) {
            throw new IllegalArgumentException("해당 권한을 가지고 있지 않습니다.");
        }
    }

    private void validatePromoteToManager(UserId executorId, UserId targetUserId) {
        validateMembershipExists(executorId);

        ChannelMembership executorMembership = getMembership(executorId);

        if (executorMembership.cannotManageRole()) {
            throw new ChannelMembershipOperationForbiddenException("역할을 변경할 권한이 없습니다.");
        }

        validateMembershipExists(targetUserId);

        ChannelMembership targetUserMembership = getMembership(targetUserId);

        if (targetUserMembership.isOwner()) {
            throw new IllegalArgumentException("채널 오너를 매니저로 강등할 수 없습니다.");
        }
        if (targetUserMembership.isManager()) {
            throw new IllegalArgumentException("이미 매니저입니다.");
        }
    }

    private void validateDemoteToMember(UserId executorId, UserId targetUserId) {
        validateMembershipExists(executorId);

        ChannelMembership executorMembership = getMembership(executorId);

        if (executorMembership.cannotManageRole()) {
            throw new ChannelMembershipOperationForbiddenException("역할을 변경할 권한이 없습니다.");
        }

        validateMembershipExists(targetUserId);

        ChannelMembership targetUserMembership = getMembership(targetUserId);

        if (targetUserMembership.isOwner()) {
            throw new IllegalArgumentException("채널 오너를 멤버로 강등시킬 수 없습니다.");
        }
        if (targetUserMembership.isMember()) {
            throw new IllegalArgumentException("이미 멤버입니다.");
        }
    }

    private void validateEditName(UserId changerId, String newName) {
        validateMembershipExists(changerId);

        ChannelMembership channelMembership = getMembership(changerId);

        if (channelMembership.cannotEditChannelName()) {
            throw new ChannelOperationForbiddenException("채널 이름을 변경할 권한이 없습니다.");
        }

        validateName(newName);
    }

    private void validateChangeChannelPolicy(UserId changerId) {
        validateMembershipExists(changerId);

        ChannelMembership executorMembership = getMembership(changerId);

        if (executorMembership.cannotChangeChannelPolicy()) {
            throw new ChannelOperationForbiddenException("채널 정책을 변경할 권한이 없습니다.");
        }
    }

    private void validateDeleteChannelPermission(UserId deleterId) {
        validateMembershipExists(deleterId);

        ChannelMembership deleterMembership = getMembership(deleterId);

        if (deleterMembership.cannotDeleteChannel()) {
            throw new ChannelOperationForbiddenException("채널을 삭제할 권한이 없습니다.");
        }
    }

    private void validateMemberEditMessage(UserId executorId) {
        validateMembershipExists(executorId);
    }

    private void validateMemberEditMessage(
            ChannelMembership executorMembership,
            UserId executorId,
            ChatMessage message
    ) {
        if (message.isNotWriter(executorId.getValue()) && executorMembership.cannotEditMessage()) {
            throw new ChannelMembershipOperationForbiddenException("메시지를 수정할 권한이 없습니다.");
        }
        if (channelPolicy.cannotEditOwnMessage()) {
            throw new ChannelOperationForbiddenException("자신의 메시지를 수정할 수 없습니다.");
        }
    }

    private void validateMemberDeleteMessage(UserId executorId) {
        validateMembershipExists(executorId);
    }

    private void validateMemberDeleteMessage(
            ChannelMembership executorMembership,
            UserId executorId, ChatMessage message
    ) {
        if (message.isNotWriter(executorId.getValue()) && executorMembership.cannotDeleteMessage()) {
            throw new ChannelMembershipOperationForbiddenException("메시지를 삭제할 권한이 없습니다.");
        }
        if (channelPolicy.cannotDeleteOwnMessage()) {
            throw new ChannelOperationForbiddenException("자신의 메시지를 삭제할 수 없습니다.");
        }
    }

    private void validateMemberSendMessageMembership(UserId senderId) {
        validateMembershipExists(senderId);
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
