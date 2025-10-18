package com.tok.pekko.infrastructure.persistence.event;

import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.common.CborSerializable;
import org.apache.pekko.actor.typed.ActorRef;

public record LoadedRecentMessagesEvent(Long channelId, int size, ActorRef<ChatChannelEntityCommand> replyTo) implements CborSerializable {
}
