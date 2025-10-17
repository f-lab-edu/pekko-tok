package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.domain.chat.common.ActorCommand;
import com.tok.pekko.domain.chat.model.ChatMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import org.apache.pekko.actor.typed.ActorRef;

public interface NodeManagerProtocol {

    interface NodeManagerActorCommand extends ActorCommand { }

    record CreateReader(ChatMessages messages, ActorRef<ClientSessionCommand> clientActorRef, Long channelId, Long userId, ActorRef<ChatChannelEntityCommand> replyTo) implements NodeManagerActorCommand { }
}
