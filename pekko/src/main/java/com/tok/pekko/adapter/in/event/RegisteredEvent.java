package com.tok.pekko.adapter.in.event;

import com.tok.pekko.adapter.in.websocket.ClientMessageSender;
import com.tok.pekko.common.CborSerializable;

public record RegisteredEvent(
        ClientMessageSender clientMessageSender,
        Long channelId,
        Long userId
) implements CborSerializable {
}
