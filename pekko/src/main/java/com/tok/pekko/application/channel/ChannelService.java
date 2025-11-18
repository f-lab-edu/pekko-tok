package com.tok.pekko.application.channel;

import com.tok.pekko.application.channel.exception.ChannelNotFoundException;
import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelStoragePort channelStoragePort;

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
        channelStoragePort.delete(channel.getChannelId());
    }

    public void changeChannelPolicy(
            Long channelId,
            Long changerId,
            boolean canEditOwnMessage,
            boolean canDeleteOwnMessage,
            boolean isPublic
    ) {
        Channel channel = channelStoragePort.findChannel(channelId, changerId)
                                            .orElseThrow();
        UserId changer = UserId.create(changerId);
        ChannelPolicy updatedChannelPolicy = new ChannelPolicy(canEditOwnMessage, canDeleteOwnMessage, isPublic);
        Channel updatedPolicyChannel = channel.changeChannelPolicy(changer, updatedChannelPolicy);

        channelStoragePort.update(updatedPolicyChannel);
    }

    public void changeChannelName(Long channelId, Long changerId, String changedName) {
        Channel channel = channelStoragePort.findChannel(channelId, changerId)
                                            .orElseThrow(ChannelNotFoundException::new);
        UserId changer = UserId.create(changerId);
        Channel updatedChannel = channel.changeName(changer, changedName);

        channelStoragePort.update(updatedChannel);
    }
}
