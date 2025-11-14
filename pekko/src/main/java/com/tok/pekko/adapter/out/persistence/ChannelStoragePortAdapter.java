package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChannelStoragePortAdapter implements ChannelStoragePort {

    @Override
    public Channel store(Channel channel) {
        // NO-OP
        return null;
    }

    @Override
    public Optional<Channel> findChannel(Long channelId, Long userId) {
        // NO-OP
        return Optional.empty();
    }

    @Override
    public void update(Channel channel) {
        // NO-OP
    }

    @Override
    public void delete(ChannelId channelId) {
        // NO-OP
    }
}
