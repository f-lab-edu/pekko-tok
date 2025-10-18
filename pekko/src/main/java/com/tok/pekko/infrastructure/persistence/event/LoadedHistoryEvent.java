package com.tok.pekko.infrastructure.persistence.event;

import com.tok.pekko.common.CborSerializable;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import org.apache.pekko.actor.typed.ActorRef;

public record LoadedHistoryEvent(
        Long channelId,
        long messageSequence,
        int size,
        ActorRef<ChatChannelReaderCommand> replyTo
) implements CborSerializable {
}
