package com.tok.pekko.application.channel;

import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.application.channel.exception.ChannelNotFoundException;
import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
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
    private final ClientSessionActorManagementService clientSessionActorManagementService;

    public void joinChannel(Long channelId, Long userId) {
        Channel channel = channelStoragePort.findChannel(channelId, userId)
                                            .orElseThrow(ChannelNotFoundException::new);

        if (!channel.isPublic()) {
            throw new ChannelMembershipOperationForbiddenException("해당 채널에 참여할 권한이 없습니다.");
        }

        channel.joinMember(UserId.create(userId), ChannelRole.MEMBER, LocalDateTime.now(clock));
        clientSessionActorManagementService.syncJoinChannel(channelId, userId);
    }

    public void inviteMember(Long channelId, Long inviterId, Long inviteeId) {
        Channel channel = channelStoragePort.findChannel(channelId, inviterId)
                                            .orElseThrow(ChannelNotFoundException::new);

        if (!channel.canUserInviteMember(UserId.create(inviterId))) {
            throw new ChannelMembershipOperationForbiddenException("멤버를 초대할 권한이 없습니다.");
        }

        channel.inviteMember(UserId.create(inviteeId), ChannelRole.MEMBER, LocalDateTime.now(clock));
        clientSessionActorManagementService.syncJoinChannel(channelId, inviteeId);
    }

    public void leaveChannel(Long channelId, Long userId) {
        Channel channel = channelStoragePort.findChannel(channelId, userId)
                                            .orElseThrow(ChannelNotFoundException::new);

        channel.leaveMember(UserId.create(userId));
        clientSessionActorManagementService.syncLeaveChannel(channelId, userId);
    }

    public void managedMemberRole(Long channelId, Long executorId, Long targetUserId, ChannelRole channelRole) {
        Channel channel = channelStoragePort.findChannel(channelId, executorId)
                                            .orElseThrow(ChannelNotFoundException::new);

        if (!channel.canUserMemberRoleManagement(UserId.create(executorId))) {
            throw new ChannelMembershipOperationForbiddenException("멤버의 역할을 변경할 권한이 없습니다.");
        }

        if (channelRole.isMember()) {
            channel.demoteToMember(UserId.create(targetUserId));
        }
        if (channelRole.isManager()) {
            channel.promoteToManager(UserId.create(targetUserId), ChannelManagePermissions.ofManager());
        }

        throw new IllegalArgumentException("오너 역할은 부여할 수 없습니다.");
    }

    public void addPermissions(Long channelId, Long grantorId, Long granteeId, ChannelPermissionType permissionType) {
        Channel channel = channelStoragePort.findChannel(channelId, grantorId)
                                            .orElseThrow(ChannelNotFoundException::new);

        if (!channel.canUserPermissionManagement(UserId.create(grantorId))) {
            throw new ChannelMembershipOperationForbiddenException("채널 권한을 추가할 권한이 없습니다.");
        }

        channel.addPermissionToManager(UserId.create(granteeId), permissionType);
    }

    public void removePermissions(
            Long channelId,
            Long grantorId,
            Long granteeId,
            ChannelPermissionType permissionType
    ) {
        Channel channel = channelStoragePort.findChannel(channelId, granteeId)
                                            .orElseThrow();

        if (!channel.canUserPermissionManagement(UserId.create(grantorId))) {
            throw new ChannelMembershipOperationForbiddenException("채널 권한을 삭제할 권한이 없습니다.");
        }

        channel.removePermissionFromManager(UserId.create(granteeId), permissionType);
    }

    public static class ChannelMembershipOperationForbiddenException extends IllegalArgumentException {

        public ChannelMembershipOperationForbiddenException(String s) {
            super(s);
        }
    }
}
