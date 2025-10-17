package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import org.apache.pekko.actor.typed.ActorRef;

public interface MessageStoragePort {

    void store(ChatMessage message);

    void findHistory(
            Long channelId,
            long messageSequence,
            int size,
            ActorRef<ChatChannelReaderCommand> replyTo
    );
}
