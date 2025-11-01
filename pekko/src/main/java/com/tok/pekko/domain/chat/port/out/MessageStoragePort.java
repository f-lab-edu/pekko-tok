package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import org.apache.pekko.actor.typed.ActorRef;

public interface MessageStoragePort {

    void store(ChatMessage message, ActorRef<ChatChannelEntityCommand> replyTo);

    void update(Long messageId, String updatedMessage, ActorRef<ChatChannelEntityCommand> replyTo);

    void delete(Long messageId, ActorRef<ChatChannelEntityCommand> replyTo);

    void findHistory(
            Long channelId,
            long messageSequence,
            int size,
            ActorRef<ClientSessionCommand> replyTo
    );

    void findRecentMessages(Long channelId, int size, ActorRef<ChatChannelEntityCommand> replyTo);
}
