package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelMembershipPort {

    void findParticipatingChannels(Long userId, ActorRef<ClientSessionCommand> replyTo);
}
