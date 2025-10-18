package com.tok.pekko.infrastructure.persistence.event;

import com.tok.pekko.common.CborSerializable;
import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import org.apache.pekko.actor.typed.ActorRef;

public record StoredEvent(ChatMessage message, ActorRef<ChatChannelEntityCommand> replyTo) implements CborSerializable {
}
