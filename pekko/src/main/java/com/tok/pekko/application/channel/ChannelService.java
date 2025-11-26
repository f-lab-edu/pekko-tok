package com.tok.pekko.application.channel;

import com.tok.pekko.application.channel.exception.ChannelNotFoundException;
import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChangeChannelPolicy;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.EditChannelName;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ClusterSharding clusterSharding;
    private final ChannelStoragePort channelStoragePort;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public ChannelId createChannel(String name, Long creatorId) {
        Channel channel = Channel.create(name, creatorId, ChannelPolicy.defaultPolicy(), LocalDateTime.now());

        return channelStoragePort.store(channel)
                                 .getChannelId();
    }

    public void deleteChannel(Long channelId, Long deleterId) {
        Channel channel = channelStoragePort.findChannel(channelId, deleterId)
                                            .orElseThrow(ChannelNotFoundException::new);
        UserId deleter = UserId.create(deleterId);

        transactionTemplate.executeWithoutResult(
                status -> {
                    channel.validateDeleteChannel(deleter);
                    channelStoragePort.delete(channel.getChannelId());
                }
        );

        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);
        EntityRef<ChannelEventHandlerCommand> channelEventHandler = getChannelEventHandler(channelId);

        channelEntity.tell(new ChannelProtocol.Shutdown());
        channelEventHandler.tell(new ChannelEventHandlerProtocol.Shutdown());
    }

    public void changeChannelPolicy(
            Long channelId,
            Long changerId,
            boolean canEditOwnMessage,
            boolean canDeleteOwnMessage,
            boolean isPublic
    ) {
        EntityRef<ChannelEntityCommand> channel = getChannelEntity(channelId);

        channel.tell(
                new ChangeChannelPolicy(UserId.create(changerId), canEditOwnMessage, canDeleteOwnMessage, isPublic)
        );
    }

    public void editChannelName(Long channelId, Long changerId, String changedName) {
        EntityRef<ChannelEntityCommand> channelEntity = getChannelEntity(channelId);

        channelEntity.tell(new EditChannelName(UserId.create(changerId), changedName));
    }

    private EntityRef<ChannelEntityCommand> getChannelEntity(Long channelId) {
        return clusterSharding.entityRefFor(
                ChannelEntity.ENTITY_TYPE_KEY,
                String.valueOf(channelId)
        );
    }

    private EntityRef<ChannelEventHandlerCommand> getChannelEventHandler(Long channelId) {
        return clusterSharding.entityRefFor(
                ChannelEventHandlerEntity.ENTITY_TYPE_KEY,
                String.valueOf(channelId)
        );
    }
}
