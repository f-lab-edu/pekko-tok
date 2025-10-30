package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.global.common.CborSerializable;

public interface ChannelReaderRegistryProtocol {

    interface ChannelReaderRegistryCommand extends CborSerializable { }

    record PongHealthCheck(Long channelId) implements ChannelReaderRegistryCommand { }
}
