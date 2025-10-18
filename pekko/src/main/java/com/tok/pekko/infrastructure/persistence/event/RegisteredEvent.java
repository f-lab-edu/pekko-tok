package com.tok.pekko.infrastructure.persistence.event;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.common.CborSerializable;

public record RegisteredEvent(
        ClientMessageSender clientMessageSender,
        Long channelId,
        Long userId
) implements CborSerializable {
}
