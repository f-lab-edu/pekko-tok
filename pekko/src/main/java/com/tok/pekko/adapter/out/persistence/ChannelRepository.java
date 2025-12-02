package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import java.util.Optional;

public interface ChannelRepository {

    Channel save(Channel channel);

    Optional<Channel> findByIdWithMembership(Long channelId, Long... memberIds);

    Optional<Channel> findById(Long channelId);

    void update(Channel channel);

    void deleteById(ChannelId channelId);
}
