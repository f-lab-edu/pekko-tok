package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelActorStoragePort {

    void find(Long channelId, ActorRef<ChannelEntityCommand> replyTo);

    void update(Channel channel, Long eventId, ActorRef<ChannelEventHandlerCommand> replyTo);
}
