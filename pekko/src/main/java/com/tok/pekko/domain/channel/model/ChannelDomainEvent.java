package com.tok.pekko.domain.channel.model;

import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import java.util.List;

public interface ChannelDomainEvent {

    record ChannelNameEdited(Long channelId, Long changerId, String newName, LocalDateTime occurredAt) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyChannelNameEdited(newName);
        }
    }

    record ChannelPolicyChanged(
            Long channelId,
            Long changerId,
            boolean canEditOwnMessage,
            boolean canDeleteOwnMessage,
            boolean isPublic,
            LocalDateTime occurredAt
    ) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyChannelPolicyChanged(canEditOwnMessage, canDeleteOwnMessage, isPublic);
        }
    }

    record UserJoined(
            Long channelId,
            Long userId,
            String role,
            List<String> managerPermissions,
            LocalDateTime joinedAt
    ) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyUserJoined(channelId, userId, role, managerPermissions, joinedAt);
        }

        @Override
        public void persistMembership(ChannelMembershipActorStoragePort membershipStoragePort, Channel channel) {
            ChannelMembership membership = channel.getMemberships().get(UserId.create(userId));

            if (membership != null) {
                membershipStoragePort.joinChannel(channel.getChannelId(), membership);
            }
        }

        @Override
        public boolean isMembershipEvent() {
            return true;
        }
    }

    record UserInvited(
            Long channelId,
            Long userId,
            String role,
            List<String> managerPermissions,
            LocalDateTime joinedAt
    ) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyUserInvited(channelId, userId, role, managerPermissions, joinedAt);
        }

        @Override
        public void persistMembership(ChannelMembershipActorStoragePort membershipStoragePort, Channel channel) {
            ChannelMembership membership = channel.getMemberships().get(UserId.create(userId));

            if (membership != null) {
                membershipStoragePort.joinChannel(channel.getChannelId(), membership);
            }
        }

        @Override
        public boolean isMembershipEvent() {
            return true;
        }
    }

    record MemberLeft(Long channelId, Long userId, LocalDateTime occurredAt) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyMemberLeft(userId);
        }

        @Override
        public void persistMembership(ChannelMembershipActorStoragePort membershipStoragePort, Channel channel) {
            membershipStoragePort.leaveChannel(channel.getChannelId(), UserId.create(userId));
        }

        @Override
        public boolean isMembershipEvent() {
            return true;
        }
    }

    record MemberKicked(Long channelId, Long userId, LocalDateTime occurredAt) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyMemberKicked(userId);
        }

        @Override
        public void persistMembership(ChannelMembershipActorStoragePort membershipStoragePort, Channel channel) {
            membershipStoragePort.kickMember(channel.getChannelId(), UserId.create(userId));
        }

        @Override
        public boolean isMembershipEvent() {
            return true;
        }
    }

    record PromotedToManager(
            Long userId,
            List<String> managerPermissions,
            LocalDateTime occurredAt
    ) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyPromotedToManager(userId, managerPermissions);
        }

        @Override
        public void persistMembership(ChannelMembershipActorStoragePort membershipStoragePort, Channel channel) {
            ChannelMembership membership = channel.getMemberships().get(UserId.create(userId));

            if (membership != null) {
                membershipStoragePort.promoteToManager(membership);
            }
        }

        @Override
        public boolean isMembershipEvent() {
            return true;
        }
    }

    record DemotedToMember(Long channelId, Long userId, LocalDateTime occurredAt) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyDemotedToMember(userId);
        }

        @Override
        public void persistMembership(ChannelMembershipActorStoragePort membershipStoragePort, Channel channel) {
            ChannelMembership membership = channel.getMemberships().get(UserId.create(userId));

            if (membership != null) {
                membershipStoragePort.demoteToMember(membership);
            }
        }

        @Override
        public boolean isMembershipEvent() {
            return true;
        }
    }

    record PermissionAdded(
            Long channelId,
            Long userId,
            String permissionType,
            LocalDateTime occurredAt
    ) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyPermissionAdded(userId, permissionType);
        }

        @Override
        public void persistMembership(ChannelMembershipActorStoragePort membershipStoragePort, Channel channel) {
            ChannelMembership membership = channel.getMemberships().get(UserId.create(userId));

            if (membership != null) {
                ChannelPermissionType permission = ChannelPermissionType.valueOf(permissionType);

                membershipStoragePort.addPermission(membership, permission);
            }
        }

        @Override
        public boolean isMembershipEvent() {
            return true;
        }
    }

    record PermissionRemoved(
            Long channelId,
            Long userId,
            String permissionType,
            LocalDateTime occurredAt
    ) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyPermissionRemoved(userId, permissionType);
        }

        @Override
        public void persistMembership(ChannelMembershipActorStoragePort membershipStoragePort, Channel channel) {
            ChannelMembership membership = channel.getMemberships().get(UserId.create(userId));

            if (membership != null) {
                ChannelPermissionType permission = ChannelPermissionType.valueOf(permissionType);

                membershipStoragePort.removePermission(membership, permission);
            }
        }

        @Override
        public boolean isMembershipEvent() {
            return true;
        }
    }

    void apply(Channel channel);

    default boolean isMembershipEvent() {
        return false;
    }

    default void persistMembership(ChannelMembershipActorStoragePort membershipStoragePort, Channel channel) {
    }
}
