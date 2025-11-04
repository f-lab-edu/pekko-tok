package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.global.common.CborSerializable;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelReaderRegistryProtocol {

    interface ChannelReaderRegistryCommand extends CborSerializable { }

    record SpawnedChannelReaderActor(Long channelId, ActorRef<ChannelReaderCommand> reader, String readerName) implements ChannelReaderRegistryCommand { }
    record ReleaseChannelReaderActor(Long userId, List<Long> channelIds) implements ChannelReaderRegistryCommand { }
    record ReportUnhealthyChannelReader(List<Long> channelIds) implements ChannelReaderRegistryCommand { }
    record ReportUnhealthyClientSession(Long userId, Long channelId) implements ChannelReaderRegistryCommand { }
}
