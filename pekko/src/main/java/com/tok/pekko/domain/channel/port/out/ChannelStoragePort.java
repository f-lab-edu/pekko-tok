package com.tok.pekko.domain.channel.port.out;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import java.util.Optional;

public interface ChannelStoragePort {

    Channel store(Channel channel);

    Optional<Channel> findChannel(Long channelId, Long... userIds);

    void update(Channel channel);

    void delete(ChannelId channelId);
}
