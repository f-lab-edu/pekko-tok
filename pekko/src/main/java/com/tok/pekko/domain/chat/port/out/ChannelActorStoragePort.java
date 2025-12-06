package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelActorStoragePort {

    void find(Long channelId, ActorRef<ChannelEntityCommand> replyTo);

    void update(
            Channel channel,
            ActorRef<ChannelEntityCommand> replyTo,
            long batchId,
            List<ChannelDomainEvent> batch
    );
}
