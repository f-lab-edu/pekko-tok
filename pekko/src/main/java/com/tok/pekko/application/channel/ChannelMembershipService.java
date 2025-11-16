package com.tok.pekko.application.channel;

import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.application.channel.exception.ChannelNotFoundException;
import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.port.out.ChannelMembershipStoragePort;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ChannelMembershipService {

    private final Clock clock;
    private final ChannelStoragePort channelStoragePort;
    private final ChannelMembershipStoragePort channelMembershipStoragePort;
    private final ClientSessionActorManagementService clientSessionActorManagementService;

    public void joinChannel(Long channelId, Long joinerId) {
        Channel channel = channelStoragePort.findChannel(channelId, joinerId)
                                            .orElseThrow(ChannelNotFoundException::new);
        UserId joiner = UserId.create(joinerId);

        channel.validateJoinMember(joiner);

        ChannelMembership joinerMembership = ChannelMembership.create(
                channel.getChannelId(),
                joiner,
                ChannelRole.MEMBER,
                LocalDateTime.now(clock)
        );

        channelMembershipStoragePort.joinChannel(channel.getChannelId(), joinerMembership);
        clientSessionActorManagementService.syncJoinChannel(channelId, joinerId);
    }

    public void inviteMember(Long channelId, Long inviterId, Long inviteeId) {
        Channel channel = channelStoragePort.findChannel(channelId, inviterId, inviteeId)
                                            .orElseThrow(ChannelNotFoundException::new);

        UserId inviter = UserId.create(inviterId);
        UserId invitee = UserId.create(inviteeId);

        channel.validateInviteMember(inviter, invitee);

        ChannelMembership inviteeMembership = ChannelMembership.create(
                channel.getChannelId(),
                invitee,
                ChannelRole.MEMBER,
                LocalDateTime.now(clock)
        );

        channelMembershipStoragePort.joinChannel(channel.getChannelId(), inviteeMembership);
        clientSessionActorManagementService.syncJoinChannel(channelId, inviteeId);
    }

    public void leaveChannel(Long channelId, Long userId) {
        Channel channel = channelStoragePort.findChannel(channelId, userId)
                                            .orElseThrow(ChannelNotFoundException::new);
        UserId user = UserId.create(userId);

        channel.validateLeaveMember(user);
        channelMembershipStoragePort.leaveChannel(channel.getChannelId(), user);
        clientSessionActorManagementService.syncLeaveChannel(channelId, userId);
    }

    public void promoteToManager(Long channelId, Long executorId, Long targetUserId) {
        Channel channel = channelStoragePort.findChannel(channelId, executorId, targetUserId)
                                            .orElseThrow(ChannelNotFoundException::new);
        UserId executor = UserId.create(executorId);
        UserId targetUser = UserId.create(targetUserId);
        ChannelMembership targetUserMembership = channel.promoteToManager(executor, targetUser);

        channelMembershipStoragePort.promoteToManager(targetUserMembership);
    }

    public void demoteToMember(Long channelId, Long executorId, Long targetUserId) {
        Channel channel = channelStoragePort.findChannel(channelId, executorId, targetUserId)
                                            .orElseThrow(ChannelNotFoundException::new);
        UserId executor = UserId.create(executorId);
        UserId targetUser = UserId.create(targetUserId);

        ChannelMembership targetUserMembership = channel.demoteToMember(executor, targetUser);

        channelMembershipStoragePort.demoteToMember(targetUserMembership);
    }

    public void addPermission(Long channelId, Long grantorId, Long granteeId, ChannelPermissionType permission) {
        Channel channel = channelStoragePort.findChannel(channelId, grantorId)
                                            .orElseThrow(ChannelNotFoundException::new);
        UserId grantor = UserId.create(grantorId);
        UserId grantee = UserId.create(granteeId);

        ChannelMembership granteeMembership = channel.getValidatedAddTarget(grantor, grantee, permission);

        channelMembershipStoragePort.addPermission(granteeMembership, permission);
    }

    public void removePermission(Long channelId, Long grantorId, Long granteeId, ChannelPermissionType permission) {
        Channel channel = channelStoragePort.findChannel(channelId, grantorId)
                                            .orElseThrow(ChannelNotFoundException::new);
        UserId grantor = UserId.create(grantorId);
        UserId grantee = UserId.create(granteeId);

        ChannelMembership granteeMembership = channel.getValidatedRemoveTarget(grantor, grantee, permission);
        channelMembershipStoragePort.removePermission(granteeMembership, permission);
    }

    public void kickMember(Long channelId, Long executorId, Long targetUserId) {
        Channel channel = channelStoragePort.findChannel(channelId, executorId, targetUserId)
                                           .orElseThrow(ChannelNotFoundException::new);
        UserId executor = UserId.create(executorId);
        UserId targetUser = UserId.create(targetUserId);

        channel.validateKickMember(executor, targetUser);
        channelMembershipStoragePort.kickMember(channel.getChannelId(), targetUser);
    }
}
