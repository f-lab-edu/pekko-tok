package com.tok.pekko.domain.channel.port.out;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.vo.ChannelId;

public interface ChannelStoragePort {

    Channel store(Channel channel);

    Channel findChannel(Long channelId);

    void update(Channel channel);

    void delete(ChannelId channelId);
}
