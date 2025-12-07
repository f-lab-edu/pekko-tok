package com.tok.pekko.application.channel;

import com.tok.pekko.application.channel.exception.ChannelNotFoundException;
import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyChannelDeleted;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyChannelNameEdited;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyChannelPolicyChanged;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelStoragePort channelStoragePort;
    private final ClusterSharding clusterSharding;
    private final java.time.Clock clock;

    public ChannelId createChannel(String name, Long creatorId) {
        Channel channel = Channel.create(name, creatorId, ChannelPolicy.defaultPolicy(), LocalDateTime.now());

        return channelStoragePort.store(channel)
                                 .getChannelId();
    }

    public void deleteChannel(Long channelId, Long deleterId) {
        Channel channel = channelStoragePort.findChannel(channelId, deleterId)
                                            .orElseThrow(ChannelNotFoundException::new);
        UserId deleter = UserId.create(deleterId);

        channel.validateDeleteChannel(deleter);
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyChannelDeleted(
                        channelId,
                        deleter,
                        LocalDateTime.now(clock)
                )
        );
        channelStoragePort.delete(channel.getChannelId());
    }

    public void changeChannelPolicy(
            Long channelId,
            Long changerId,
            boolean canEditOwnMessage,
            boolean canDeleteOwnMessage,
            boolean isPublic
    ) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyChannelPolicyChanged(
                        channelId,
                        UserId.create(changerId),
                        canEditOwnMessage,
                        canDeleteOwnMessage,
                        isPublic,
                        LocalDateTime.now(clock)
                )
        );
    }

    public void changeChannelName(Long channelId, Long changerId, String changedName) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        channelEntity.tell(
                new ApplyChannelNameEdited(
                        channelId,
                        UserId.create(changerId),
                        changedName,
                        LocalDateTime.now(clock)
                )
        );
    }

    private EntityRef<ChannelEntityCommand> getChannelEntity(Long channelId) {
        return clusterSharding.entityRefFor(ChannelEntity.ENTITY_TYPE_KEY, String.valueOf(channelId));
    }
}
