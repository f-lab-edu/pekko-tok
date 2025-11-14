package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelMembershipActorMessagePort {

    void sendParticipatingChannels(Long userId, ActorRef<ClientSessionCommand> replyTo);
}
