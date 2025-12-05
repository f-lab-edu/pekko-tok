package com.tok.pekko.domain.channel.model;

import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.user.model.vo.UserId;
import com.tok.pekko.global.common.ActorThreadSafe;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(of = "channelId")
@ActorThreadSafe
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
        if (channelPolicy.isPrivate()) {
            throw new ChannelMembershipOperationForbiddenException("비공개 채널입니다. 초대를 통해 참여할 수 있습니다.");
        }
        if (memberships.containsKey(userId)) {
            throw new IllegalArgumentException("이미 채널에 참여한 사용자입니다.");
        }

        ChannelMembership joinerMembership = ChannelMembership.create(this.channelId, userId, role, joinedAt);

        memberships.put(userId, joinerMembership);
        return joinerMembership;
    }

    public ChannelMembership inviteMember(UserId inviterId, UserId inviteeId, LocalDateTime invitedAt) {
        ChannelMembership inviterMembership = memberships.get(inviterId);

        if (inviterMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (inviterMembership.cannotInviteMember()) {
            throw new ChannelMembershipOperationForbiddenException("멤버 초대 권한이 없습니다.");
        }
        if (memberships.containsKey(inviteeId)) {
            throw new IllegalArgumentException("이미 해당 채널에 참여한 사용자입니다.");
        }

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
        ChannelMembership channelMembership = memberships.get(memberId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (channelMembership.isOwner()) {
            throw new IllegalArgumentException("채널 오너는 채널에서 나갈 수 없습니다.");
        }

        return memberships.remove(memberId);
    }

    public ChannelMembership kickMember(UserId executorId, UserId targetUserId) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!executorMembership.canKickMember()) {
            throw new ChannelMembershipOperationForbiddenException("멤버를 강퇴할 권한이 없습니다.");
        }

        ChannelMembership targetUserMembership = memberships.get(targetUserId);

        if (targetUserMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (targetUserMembership.isNotMember()) {
            throw new IllegalArgumentException("멤버만 강퇴할 수 있습니다.");
        }

        return memberships.remove(targetUserId);
    }

    public ChannelMembership addPermission(UserId grantorId, UserId granteeId, ChannelPermissionType permission) {
        ChannelMembership grantorMembership = memberships.get(grantorId);

        if (grantorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (grantorMembership.cannotManagePermission()) {
            throw new ChannelMembershipOperationForbiddenException("매니저의 권한을 추가할 권한이 없습니다.");
        }

        ChannelMembership granteeMembership = memberships.get(granteeId);

        if (granteeMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (granteeMembership.isNotManager()) {
            throw new IllegalArgumentException("해당 멤버는 매니저가 아닙니다.");
        }
        if (granteeMembership.hasPermission(permission)) {
            throw new IllegalArgumentException("이미 해당 권한을 가지고 있습니다.");
        }

        ChannelMembership addedPermissionMembership = granteeMembership.addPermission(permission);

        memberships.put(granteeId, addedPermissionMembership);

        return addedPermissionMembership;
    }

    public ChannelMembership removePermission(UserId grantorId, UserId granteeId, ChannelPermissionType permission) {
        ChannelMembership grantorMembership = memberships.get(grantorId);

        if (grantorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        if (grantorMembership.cannotManagePermission()) {
            throw new ChannelMembershipOperationForbiddenException("매니저의 권한을 추가할 권한이 없습니다.");
        }

        ChannelMembership granteeMembership = memberships.get(granteeId);

        if (granteeMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (granteeMembership.isNotManager()) {
            throw new IllegalArgumentException("해당 멤버는 매니저가 아닙니다.");
        }
        if (granteeMembership.lacksPermission(permission)) {
            throw new IllegalArgumentException("해당 권한을 가지고 있지 않습니다.");
        }

        ChannelMembership removedPermissionMembership = granteeMembership.removePermission(permission);

        memberships.put(granteeId, removedPermissionMembership);

        return removedPermissionMembership;
    }

    public void syncMembership(ChannelMembership membership) {
        memberships.put(membership.getUserId(), membership);
    }

    public ChannelMembership promoteToManager(UserId executorId, UserId targetUserId) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (executorMembership.cannotManageRole()) {
            throw new ChannelMembershipOperationForbiddenException("역할을 변경할 권한이 없습니다.");
        }

        ChannelMembership targetUserMembership = memberships.get(targetUserId);

        if (targetUserMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (targetUserMembership.isOwner()) {
            throw new IllegalArgumentException("채널 오너를 매니저로 강등할 수 없습니다.");
        }
        if (targetUserMembership.isManager()) {
            throw  new IllegalArgumentException("이미 매니저입니다.");
        }

        return targetUserMembership.promoteToManager(ChannelManagePermissions.ofManager());
    }

    public ChannelMembership demoteToMember(UserId executorId, UserId targetUserId) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (executorMembership.cannotManageRole()) {
            throw new ChannelMembershipOperationForbiddenException("역할을 변경할 권한이 없습니다.");
        }

        ChannelMembership targetUserMembership = memberships.get(targetUserId);

        if (targetUserMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (targetUserMembership.isOwner()) {
            throw new IllegalArgumentException("채널 오너를 멤버로 강등시킬 수 없습니다.");
        }
        if (targetUserMembership.isMember()) {
            throw new IllegalArgumentException("이미 멤버입니다.");
        }

        return targetUserMembership.demoteToMember();
    }

    public Channel editName(UserId changerId, String newName) {
        ChannelMembership channelMembership = memberships.get(changerId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (channelMembership.cannotEditChannelName()) {
            throw new ChannelOperationForbiddenException("채널 이름을 변경할 권한이 없습니다.");
        }

        validateName(newName);

        this.name = newName;
        return this;
    }

    public Channel changeChannelPolicy(UserId changerId, ChannelPolicy channelPolicy) {
        ChannelMembership executorMembership = memberships.get(changerId);

        if (executorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (executorMembership.cannotChangeChannelPolicy()) {
            throw new ChannelOperationForbiddenException("채널 정책을 변경할 권한이 없습니다.");
        }

        this.channelPolicy = channelPolicy;
        return this;
    }

    public void validateDeleteChannel(UserId deleterId) {
        ChannelMembership deleterMembership = memberships.get(deleterId);

        if (deleterMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (deleterMembership.cannotDeleteChannel()) {
            throw new ChannelOperationForbiddenException("채널을 삭제할 권한이 없습니다.");
        }
    }

    public void validateMemberEditMessage(UserId executorId, ChatMessage message) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (message.isNotWriter(executorId.getValue()) && executorMembership.cannotEditMessage()) {
            throw new ChannelMembershipOperationForbiddenException("메시지를 수정할 권한이 없습니다.");
        }
        if (channelPolicy.cannotEditOwnMessage()) {
            throw new ChannelOperationForbiddenException("자신의 메시지를 수정할 수 없습니다.");
        }
    }

    public void validateMemberDeleteMessage(UserId executorId, ChatMessage message) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (message.isNotWriter(executorId.getValue()) && executorMembership.cannotDeleteMessage()) {
            throw new ChannelMembershipOperationForbiddenException("메시지를 삭제할 권한이 없습니다.");
        }
        if (channelPolicy.cannotDeleteOwnMessage()) {
            throw new ChannelOperationForbiddenException("자신의 메시지를 삭제할 수 없습니다.");
        }
    }

    public void validateMemberSendMessage(UserId senderId) {
        ChannelMembership senderMembership = memberships.get(senderId);

        if (senderMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
    }

    public boolean canJoinUser(UserId userId) {
        if (channelPolicy.isPrivate()) {
            return false;
        }
        return !memberships.containsKey(userId);
    }

    public boolean canInviteMember(UserId inviterId, UserId inviteeId) {
        ChannelMembership inviterMembership = memberships.get(inviterId);

        if (inviterMembership == null) {
            return false;
        }
        if (inviterMembership.cannotInviteMember()) {
            return false;
        }
        return !memberships.containsKey(inviteeId);
    }

    public boolean canLeaveMember(UserId memberId) {
        ChannelMembership channelMembership = memberships.get(memberId);

        if (channelMembership == null) {
            return false;
        }
        return !channelMembership.isOwner();
    }

    public boolean canKickMember(UserId executorId, UserId targetUserId) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            return false;
        }
        if (!executorMembership.canKickMember()) {
            return false;
        }

        ChannelMembership targetUserMembership = memberships.get(targetUserId);

        if (targetUserMembership == null) {
            return false;
        }
        return targetUserMembership.isMember();
    }

    public boolean canAddPermission(UserId grantorId, UserId granteeId, ChannelPermissionType permission) {
        ChannelMembership grantorMembership = memberships.get(grantorId);

        if (grantorMembership == null) {
            return false;
        }
        if (grantorMembership.cannotManagePermission()) {
            return false;
        }

        ChannelMembership granteeMembership = memberships.get(granteeId);

        if (granteeMembership == null) {
            return false;
        }
        if (granteeMembership.isNotManager()) {
            return false;
        }
        return granteeMembership.lacksPermission(permission);
    }

    public boolean canRemovePermission(UserId grantorId, UserId granteeId, ChannelPermissionType permission) {
        ChannelMembership grantorMembership = memberships.get(grantorId);

        if (grantorMembership == null) {
            return false;
        }
        if (grantorMembership.cannotManagePermission()) {
            return false;
        }

        ChannelMembership granteeMembership = memberships.get(granteeId);

        if (granteeMembership == null) {
            return false;
        }
        if (granteeMembership.isNotManager()) {
            return false;
        }

        return granteeMembership.hasPermission(permission);
    }

    public boolean canPromoteToManager(UserId executorId, UserId targetUserId) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            return false;
        }
        if (executorMembership.cannotManageRole()) {
            return false;
        }

        ChannelMembership targetUserMembership = memberships.get(targetUserId);

        if (targetUserMembership == null) {
            return false;
        }
        if (targetUserMembership.isOwner()) {
            return false;
        }

        return targetUserMembership.isNotManager();
    }

    public boolean canDemoteToMember(UserId executorId, UserId targetUserId) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            return false;
        }
        if (executorMembership.cannotManageRole()) {
            return false;
        }

        ChannelMembership targetUserMembership = memberships.get(targetUserId);

        if (targetUserMembership == null) {
            return false;
        }
        if (targetUserMembership.isOwner()) {
            return false;
        }

        return !targetUserMembership.isMember();
    }

    public boolean canEditName(UserId changerId) {
        ChannelMembership channelMembership = memberships.get(changerId);

        if (channelMembership == null) {
            return false;
        }

        return channelMembership.canEditChannelName();
    }

    public boolean canChangeChannelPolicy(UserId changerId) {
        ChannelMembership executorMembership = memberships.get(changerId);

        if (executorMembership == null) {
            return false;
        }

        return executorMembership.canChangeChannelPolicy();
    }

    public boolean canDeleteChannel(UserId deleterId) {
        ChannelMembership deleterMembership = memberships.get(deleterId);

        if (deleterMembership == null) {
            return false;
        }
        return deleterMembership.canDeleteChannel();
    }

    public boolean canMemberEditMessage(UserId executorId, ChatMessage message) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            return false;
        }
        if (message.isNotWriter(executorId.getValue()) && executorMembership.cannotEditMessage()) {
            return false;
        }

        return !channelPolicy.cannotEditOwnMessage();
    }

    public boolean canMemberDeleteMessage(UserId executorId, ChatMessage message) {
        ChannelMembership executorMembership = memberships.get(executorId);

        if (executorMembership == null) {
            return false;
        }
        if (message.isNotWriter(executorId.getValue()) && executorMembership.cannotDeleteMessage()) {
            return false;
        }

        return !channelPolicy.cannotDeleteOwnMessage();
    }

    public boolean canMemberSendMessage(UserId senderId) {
        ChannelMembership senderMembership = memberships.get(senderId);

        return senderMembership != null;
    }

    public void applyEvents(List<ChannelDomainEvent> events) {
        events.forEach(this::applyEvent);
    }

    private void applyEvent(ChannelDomainEvent event) {
        event.apply(this);
    }

    private ChannelMembership createMembershipFromPrimitives(
            Long channelId,
            Long userId,
            String role,
            List<String> managerPermissions,
            LocalDateTime joinedAt
    ) {
        ChannelRole channelRole = ChannelRole.find(role);
        ChannelId channelIdValue = ChannelId.create(channelId);
        UserId userIdValue = UserId.create(userId);

        if (channelRole.isManager()) {
            Set<ChannelPermissionType> permissions = toPermissionSet(managerPermissions);
            ChannelManagePermissions managePermissions = ChannelManagePermissions.ofManager(permissions);

            return ChannelMembership.createManager(channelIdValue, userIdValue, managePermissions, joinedAt);
        }

        return ChannelMembership.create(channelIdValue, userIdValue, channelRole, joinedAt);
    }

    private Set<ChannelPermissionType> toPermissionSet(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return EnumSet.noneOf(ChannelPermissionType.class);
        }

        EnumSet<ChannelPermissionType> permissionSet = EnumSet.noneOf(ChannelPermissionType.class);
        permissions.forEach(permission -> permissionSet.add(ChannelPermissionType.valueOf(permission)));

        return permissionSet;
    }

    void applyChannelNameEdited(String newName) {
        validateName(newName);

        this.name = newName;
    }

    void applyChannelPolicyChanged(boolean canEditOwnMessage, boolean canDeleteOwnMessage, boolean isPublic) {
        this.channelPolicy = new ChannelPolicy(canEditOwnMessage, canDeleteOwnMessage, isPublic);
    }

    void applyUserJoined(Long channelId, Long userId, String role, List<String> managerPermissions, LocalDateTime joinedAt) {
        UserId userIdValue = UserId.create(userId);

        if (!canJoinUser(userIdValue)) {
            return;
        }

        ChannelMembership membership = createMembershipFromPrimitives(channelId, userId, role, managerPermissions, joinedAt);

        memberships.put(membership.getUserId(), membership);
    }

    void applyUserInvited(Long channelId, Long userId, String role, List<String> managerPermissions, LocalDateTime joinedAt) {
        applyUserJoined(channelId, userId, role, managerPermissions, joinedAt);
    }

    void applyMemberLeft(Long userId) {
        UserId userIdValue = UserId.create(userId);
        ChannelMembership membership = memberships.get(userIdValue);

        if (membership == null || membership.isOwner()) {
            return;
        }

        memberships.remove(userIdValue);
    }

    void applyMemberKicked(Long userId) {
        UserId userIdValue = UserId.create(userId);
        ChannelMembership membership = memberships.get(userIdValue);

        if (membership == null || membership.isNotMember()) {
            return;
        }

        memberships.remove(userIdValue);
    }

    void applyPromotedToManager(Long userId, List<String> managerPermissions) {
        UserId userIdValue = UserId.create(userId);
        ChannelMembership existingMembership = memberships.get(userIdValue);

        if (existingMembership == null || existingMembership.isOwner() || existingMembership.isManager()) {
            return;
        }

        Set<ChannelPermissionType> permissions = toPermissionSet(managerPermissions);
        ChannelMembership membership = existingMembership.promoteToManager(ChannelManagePermissions.ofManager(permissions));

        memberships.put(membership.getUserId(), membership);
    }

    void applyDemotedToMember(Long userId) {
        ChannelMembership existing = memberships.get(UserId.create(userId));

        if (existing == null) {
            return;
        }

        ChannelMembership membership = existing.demoteToMember();

        memberships.put(membership.getUserId(), membership);
    }

    void applyPermissionAdded(Long userId, String permissionType) {
        ChannelMembership membership = memberships.get(UserId.create(userId));

        if (membership == null) {
            return;
        }
        if (membership.isNotManager()) {
            return;
        }

        ChannelPermissionType permission = ChannelPermissionType.valueOf(permissionType);
        ChannelMembership updated = membership.addPermission(permission);

        memberships.put(updated.getUserId(), updated);
    }

    void applyPermissionRemoved(Long userId, String permissionType) {
        ChannelMembership membership = memberships.get(UserId.create(userId));

        if (membership == null) {
            return;
        }
        if (membership.isNotManager()) {
            return;
        }

        ChannelPermissionType permission = ChannelPermissionType.valueOf(permissionType);
        ChannelMembership updated = membership.removePermission(permission);

        memberships.put(updated.getUserId(), updated);
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
