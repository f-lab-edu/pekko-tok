package com.tok.pekko.infrastructure.persistence.event;

import com.tok.pekko.common.CborSerializable;
import com.tok.pekko.domain.chat.model.ChatMessage;

public record StoredEvent(ChatMessage message) implements CborSerializable {
}
