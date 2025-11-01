package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.port.in.ChannelProtocol;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncDeletedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncUpdatedMessage;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundHistory;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorRef;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class MessageStorageAdapter implements MessageStoragePort {

    private final MessageRepository messageRepository;

    @Override
    public void store(ChatMessage message, ActorRef<ChannelEntityCommand> replyTo) {
        Mono.fromCallable(() -> messageRepository.save(message))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(persistedMessage -> replyTo.tell(new SyncPersistedMessage(persistedMessage)))
            .subscribe();
    }

    @Override
    public void update(Long messageId, String updatedMessage, ActorRef<ChannelEntityCommand> replyTo) {
        Mono.fromRunnable(() -> messageRepository.update(messageId, updatedMessage))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(ignored -> replyTo.tell(new SyncUpdatedMessage(messageId, updatedMessage)))
            .subscribe();
    }

    @Override
    public void delete(Long messageId, ActorRef<ChannelEntityCommand> replyTo) {
        Mono.fromRunnable(() -> messageRepository.delete(messageId))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(ignored -> replyTo.tell(new SyncDeletedMessage(messageId)))
            .subscribe();
    }

    @Override
    public void findHistory(
            Long channelId,
            long messageSequence,
            int size,
            ActorRef<ClientSessionCommand> replyTo
    ) {
        Mono.fromCallable(() -> messageRepository.findHistory(channelId, messageSequence, size))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(history -> replyTo.tell(new FoundHistory(history)))
            .subscribe();
    }

    @Override
    public void findRecentMessages(Long channelId, int size, ActorRef<ChannelEntityCommand> replyTo) {
        Mono.fromCallable(() -> messageRepository.findLatest(channelId, size))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(messages -> replyTo.tell(new ChannelProtocol.SyncRecentMessages(messages)))
            .subscribe();
    }
}
