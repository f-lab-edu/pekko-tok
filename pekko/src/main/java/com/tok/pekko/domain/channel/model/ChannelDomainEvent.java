package com.tok.pekko.domain.channel.model;

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
    }

    record MemberLeft(Long channelId, Long userId, LocalDateTime occurredAt) implements ChannelDomainEvent {
        @Override
        public void apply(Channel channel) {
            channel.applyMemberLeft(userId);
        }
    }

    record MemberKicked(Long channelId, Long userId, LocalDateTime occurredAt) implements ChannelDomainEvent {
        @Override
        public void apply(Channel channel) {
            channel.applyMemberKicked(userId);
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
    }

    record DemotedToMember(Long channelId, Long userId, LocalDateTime occurredAt) implements ChannelDomainEvent {

        @Override
        public void apply(Channel channel) {
            channel.applyDemotedToMember(userId);
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
    }

    void apply(Channel channel);
}
