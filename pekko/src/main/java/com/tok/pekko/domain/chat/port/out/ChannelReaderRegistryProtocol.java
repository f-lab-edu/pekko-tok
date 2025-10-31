package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.global.common.CborSerializable;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelReaderRegistryProtocol {

    interface ChannelReaderRegistryCommand extends CborSerializable { }

    record SpawnedChannelReaderActor(Long channelId, ActorRef<ChatChannelReaderCommand> reader, String readerName) implements ChannelReaderRegistryCommand { }
    record PongHealthCheck(Long channelId) implements ChannelReaderRegistryCommand { }
}
