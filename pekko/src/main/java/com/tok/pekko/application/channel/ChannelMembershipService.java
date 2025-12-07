package com.tok.pekko.application.channel;

import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyDemotedToMember;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyMemberKicked;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyMemberLeft;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyPermissionAdded;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyPermissionRemoved;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyPromotedToManager;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyUserInvited;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyUserJoined;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ChannelMembershipService {

    private final Clock clock;
    private final ClusterSharding clusterSharding;
    private final ClientSessionActorManagementService clientSessionActorManagementService;

    public void joinChannel(Long channelId, Long joinerId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyUserJoined(
                        channelId,
                        UserId.create(joinerId),
                        ChannelRole.MEMBER.name(),
                        List.of(),
                        LocalDateTime.now(clock)
                )
        );
        clientSessionActorManagementService.syncJoinChannel(channelId, joinerId);
    }

    public void inviteMember(Long channelId, Long inviterId, Long inviteeId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyUserInvited(
                        channelId,
                        UserId.create(inviterId),
                        UserId.create(inviteeId),
                        ChannelRole.MEMBER.name(),
                        List.of(),
                        LocalDateTime.now(clock)
                )
        );
        clientSessionActorManagementService.syncJoinChannel(channelId, inviteeId);
    }

    public void leaveChannel(Long channelId, Long userId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyMemberLeft(
                        channelId,
                        UserId.create(userId),
                        LocalDateTime.now(clock)
                )
        );
        clientSessionActorManagementService.syncLeaveChannel(channelId, userId);
    }

    public void promoteToManager(Long channelId, Long executorId, Long targetUserId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyPromotedToManager(
                        channelId,
                        UserId.create(executorId),
                        UserId.create(targetUserId),
                        ChannelManagePermissions.ofManager()
                                                .getAll()
                                                .stream()
                                                .map(ChannelPermissionType::name)
                                                .toList(),
                        LocalDateTime.now(clock)
                )
        );
    }

    public void demoteToMember(Long channelId, Long executorId, Long targetUserId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyDemotedToMember(
                        channelId,
                        UserId.create(executorId),
                        UserId.create(targetUserId),
                        LocalDateTime.now(clock)
                )
        );
    }

    public void addPermission(Long channelId, Long grantorId, Long granteeId, ChannelPermissionType permission) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyPermissionAdded(
                        channelId,
                        UserId.create(grantorId),
                        UserId.create(granteeId),
                        permission.name(),
                        LocalDateTime.now(clock)
                )
        );
    }

    public void removePermission(Long channelId, Long grantorId, Long granteeId, ChannelPermissionType permission) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyPermissionRemoved(
                        channelId,
                        UserId.create(grantorId),
                        UserId.create(granteeId),
                        permission.name(),
                        LocalDateTime.now(clock)
                )
        );
    }

    public void kickMember(Long channelId, Long executorId, Long targetUserId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyMemberKicked(
                        channelId,
                        UserId.create(executorId),
                        UserId.create(targetUserId),
                        LocalDateTime.now(clock)
                )
        );
    }

    private EntityRef<ChannelEntityCommand> getChannelEntity(Long channelId) {
        return clusterSharding.entityRefFor(ChannelEntity.ENTITY_TYPE_KEY, String.valueOf(channelId));
    }
}
