package com.tok.pekko.application.channel;

import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.AddPermission;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.DemoteToMember;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.InviteUser;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.JoinUser;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.KickMember;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.LeaveMember;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.PromoteToManager;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RemovePermission;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChannelMembershipService {

    private final ClusterSharding clusterSharding;
    private final ClientSessionActorManagementService clientSessionActorManagementService;

    public void joinChannel(Long channelId, Long joinerId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);

        channelEntity.ask(
                (ActorRef<ChannelEntityCommand> replyTo) -> new JoinUser(
                        UserId.create(joinerId),
                        replyTo
                ),
                Duration.ofSeconds(3L)
        ).thenAccept(response -> clientSessionActorManagementService.syncJoinChannel(channelId, joinerId));
    }

    public void inviteMember(Long channelId, Long inviterId, Long inviteeId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);

         channelEntity.tell(new InviteUser(UserId.create(inviterId), UserId.create(inviteeId)));
    }

    public void leaveChannel(Long channelId, Long memberId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);

        channelEntity.tell(new LeaveMember(UserId.create(memberId)));
    }

    public void promoteToManager(Long channelId, Long executorId, Long targetUserId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);

        channelEntity.tell(new PromoteToManager(UserId.create(executorId), UserId.create(targetUserId)));
    }

    public void demoteToMember(Long channelId, Long executorId, Long targetUserId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);

        channelEntity.tell(new DemoteToMember(UserId.create(executorId), UserId.create(targetUserId)));
    }

    public void addPermission(Long channelId, Long grantorId, Long granteeId, ChannelPermissionType permission) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);

        channelEntity.tell(new AddPermission(UserId.create(grantorId), UserId.create(granteeId), permission));
    }

    public void removePermission(Long channelId, Long grantorId, Long granteeId, ChannelPermissionType permission) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);

        channelEntity.tell(new RemovePermission(UserId.create(grantorId), UserId.create(granteeId), permission));
    }

    public void kickMember(Long channelId, Long executorId, Long targetUserId) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);

        channelEntity.tell(new KickMember(UserId.create(executorId), UserId.create(targetUserId)));
    }

    private EntityRef<ChannelEntityCommand> getChannelEntity(Long channelId) {
        return clusterSharding.entityRefFor(
                ChannelEntity.ENTITY_TYPE_KEY,
                String.valueOf(channelId)
        );
    }
}
