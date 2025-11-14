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

    public void joinMember(UserId userId, ChannelRole role, LocalDateTime joinedAt) {
        if (!channelPolicy.isPublic()) {
            throw new IllegalArgumentException("비공개 채널입니다. 초대를 통해 참여할 수 있습니다.");
        }
        if (memberships.containsKey(userId)) {
            throw new IllegalArgumentException("이미 채널에 참여한 사용자입니다.");
        }

        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);

        memberships.put(userId, membership);
    }

    public void inviteMember(UserId userId, ChannelRole role, LocalDateTime joinedAt) {
        if (memberships.containsKey(userId)) {
            throw new IllegalArgumentException("이미 채널에 참여한 사용자입니다.");
        }

        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);
        memberships.put(userId, membership);
    }

    public void promoteToManager(UserId userId, ChannelManagePermissions permissions) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new IllegalArgumentException("채널 멤버를 찾을 수 없습니다.");
        }

        ChannelMembership managerChannelMembership = channelMembership.promoteToManager(permissions);

        memberships.put(userId, managerChannelMembership);
    }

    public void demoteToMember(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (channelMembership.isMember()) {
            throw new IllegalArgumentException("이미 멤버입니다.");
        }
        if (channelMembership.isOwner()) {
            throw new IllegalArgumentException("오너는 강등할 수 없습니다.");
        }

        ChannelMembership memberChannelMembership = channelMembership.demoteToMember();

        memberships.put(userId, memberChannelMembership);
    }

    public void updateManagerPermissions(UserId managerId, ChannelManagePermissions newPermissions) {
        ChannelMembership channelMembership = memberships.get(managerId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!channelMembership.isManager()) {
            throw new IllegalArgumentException("해당 멤버는 매니저가 아닙니다.");
        }

        ChannelMembership updatedChannelMembership = channelMembership.updatePermissions(newPermissions);

        memberships.put(managerId, updatedChannelMembership);
    }

    public void addPermissionToManager(UserId managerId, ChannelPermissionType permission) {
        ChannelMembership channelMembership = memberships.get(managerId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!channelMembership.isManager()) {
            throw new IllegalArgumentException("해당 멤버는 매니저가 아닙니다.");
        }

        ChannelMembership updatedChannelMembership = channelMembership.addPermission(permission);

        memberships.put(managerId, updatedChannelMembership);
    }

    public void removePermissionFromManager(UserId managerId, ChannelPermissionType permission) {
        ChannelMembership channelMembership = memberships.get(managerId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (!channelMembership.isManager()) {
            throw new IllegalArgumentException("해당 멤버는 매니저가 아닙니다.");
        }

        ChannelMembership updatedChannelMembership = channelMembership.removePermission(permission);

        memberships.put(managerId, updatedChannelMembership);
    }

    public void leaveMember(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }
        if (channelMembership.isOwner()) {
            throw new IllegalArgumentException("오너는 채널에서 나갈 수 없습니다.");
        }

        memberships.remove(userId);
    }

    public boolean canUserChangeChannelPolicy(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        return channelMembership.canChangeChannelPolicy();
    }

    public boolean canMemberEditMessage(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        return channelMembership.canEditMessage(channelPolicy);
    }

    public boolean canMemberDeleteMessage(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        return channelMembership.canDeleteMessage(channelPolicy);
    }

    public boolean canUserEditChannelName(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        return channelMembership.canEditChannelName();
    }

    public boolean canUserKickMember(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        return channelMembership.canKickMember();
    }

    public boolean canUserInviteMember(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        return channelMembership.canInviteMember();
    }

    public boolean canUserMemberRoleManagement(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        return channelMembership.canMemberRoleManagement();
    }

    public boolean canUserPermissionManagement(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        return channelMembership.canPermissionManagement();
    }

    public boolean canUserDeleteChannel(UserId userId) {
        ChannelMembership channelMembership = memberships.get(userId);

        if (channelMembership == null) {
            throw new ChannelMembershipNotFoundException();
        }

        return channelMembership.canDeleteChannel();
    }

    public Channel changeName(String newName) {
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

    public Channel changeChannelPolicy(ChannelPolicy channelPolicy) {
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

    public static class ChannelMembershipNotFoundException extends IllegalArgumentException {

        public ChannelMembershipNotFoundException() {
            super("채널 멤버를 찾을 수 없습니다.");
        }
    }
}
