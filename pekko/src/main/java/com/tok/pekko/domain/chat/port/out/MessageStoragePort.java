package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import org.apache.pekko.actor.typed.ActorRef;

public interface MessageStoragePort {

    void store(ChatMessage message, ActorRef<ChannelEntityCommand> replyTo);

    void update(Long messageId, String updatedMessage, ActorRef<ChannelEntityCommand> replyTo);

    void update(Long eventId, ChatMessage updatedMessage, ActorRef<ChannelEventHandlerCommand> replyTo);

    void delete(Long messageId, ActorRef<ChannelEntityCommand> replyTo);

    void delete(Long eventId, Long messageId, ActorRef<ChannelEventHandlerCommand> replyTo);

    void findHistory(
            Long channelId,
            long messageSequence,
            int size,
            ActorRef<ClientSessionCommand> replyTo
    );

    void findRecentMessages(Long channelId, int size, ActorRef<ChannelEntityCommand> replyTo);
}
