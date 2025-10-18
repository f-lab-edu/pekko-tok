package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.infrastructure.persistence.event.LoadedHistoryEvent;
import com.tok.pekko.infrastructure.persistence.event.LoadedRecentMessagesEvent;
import com.tok.pekko.infrastructure.persistence.event.StoredEvent;
import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.eventstream.EventStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageStorageEventStreamAdapter implements MessageStoragePort {

    private final ObjectProvider<ActorSystem<?>> actorSystemProvider;

    @Override
    public void store(ChatMessage message, ActorRef<ChatChannelEntityCommand> replyTo) {
        actorSystemProvider.getObject()
                           .eventStream()
                           .tell(new EventStream.Publish<>(new StoredEvent(message, replyTo)));
    }

    @Override
    public void findHistory(
            Long channelId,
            long messageSequence,
            int size,
            ActorRef<ChatChannelReaderCommand> replyTo
    ) {
        actorSystemProvider.getObject()
                           .eventStream()
                           .tell(
                                   new EventStream.Publish<>(
                                           new LoadedHistoryEvent(channelId, messageSequence, size, replyTo)
                                   )
                           );
    }

    @Override
    public void findRecentMessages(Long channelId, int size, ActorRef<ChatChannelEntityCommand> replyTo) {
        actorSystemProvider.getObject()
                           .eventStream()
                           .tell(
                                   new EventStream.Publish<>(
                                           new LoadedRecentMessagesEvent(channelId, size, replyTo)
                                   )
                           );
    }
}
