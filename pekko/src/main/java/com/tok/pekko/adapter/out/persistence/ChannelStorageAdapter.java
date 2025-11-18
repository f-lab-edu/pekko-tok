package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChannelStorageAdapter implements ChannelStoragePort {

    private final ChannelRepository channelRepository;

    @Override
    public Channel store(Channel channel) {
        return channelRepository.save(channel);
    }

    @Override
    public Optional<Channel> findChannel(Long channelId, Long... userIds) {
        return channelRepository.findByIdWithMembership(channelId, userIds);
    }

    @Override
    public void update(Channel channel) {
        channelRepository.update(channel);
    }

    @Override
    public void delete(ChannelId channelId) {
        channelRepository.deleteById(channelId);
    }
}
