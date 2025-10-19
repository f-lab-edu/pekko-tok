package com.tok.pekko.application.actor.event;

import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.global.common.CborSerializable;

public record RegisteredEvent(
        ClientMessageSender clientMessageSender,
        Long channelId,
        Long userId
) implements CborSerializable {
}
