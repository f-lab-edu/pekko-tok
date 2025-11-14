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

        if (!channel.canUserDeleteChannel(UserId.create(deleterId))) {
            throw new ChannelOperationForbiddenException("채널 삭제 권한이 없습니다.");
        }

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

        if (!channel.canUserChangeChannelPolicy(UserId.create(changerId))) {
            throw new ChannelOperationForbiddenException("채널 정책 변경 권한이 없습니다.");
        }

        ChannelPolicy updatedChannelPolicy = new ChannelPolicy(canEditOwnMessage, canDeleteOwnMessage, isPublic);
        Channel updatedChannel = channel.changeChannelPolicy(updatedChannelPolicy);

        channelStoragePort.update(updatedChannel);
    }

    public void changeChannelName(Long channelId, Long changerId, String changedName) {
        Channel channel = channelStoragePort.findChannel(channelId, changerId)
                                            .orElseThrow(ChannelNotFoundException::new);

        if (!channel.canUserEditChannelName(UserId.create(changerId))) {
            throw new ChannelOperationForbiddenException("채널명 변경 권한이 없습니다.");
        }

        Channel updatedChannel = channel.changeName(changedName);

        channelStoragePort.update(updatedChannel);
    }

    public static class ChannelOperationForbiddenException extends IllegalArgumentException {

        public ChannelOperationForbiddenException(String s) {
            super(s);
        }
    }
}
