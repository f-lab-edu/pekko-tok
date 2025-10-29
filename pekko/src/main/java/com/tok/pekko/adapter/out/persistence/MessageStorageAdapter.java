package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.HistoryFound;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncDeletedMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncUpdatedMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.model.ChatMessage;
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
    public void store(ChatMessage message, ActorRef<ChatChannelEntityCommand> replyTo) {
        Mono.fromCallable(() -> messageRepository.save(message))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(persistedMessage -> replyTo.tell(new SyncPersistedMessage(persistedMessage)))
            .subscribe();
    }

    @Override
    public void update(Long messageId, String updatedMessage, ActorRef<ChatChannelEntityCommand> replyTo) {
        Mono.fromRunnable(() -> messageRepository.update(messageId, updatedMessage))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(ignored -> replyTo.tell(new SyncUpdatedMessage(messageId, updatedMessage)))
            .subscribe();
    }

    @Override
    public void delete(Long messageId, ActorRef<ChatChannelEntityCommand> replyTo) {
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
            ActorRef<ChatChannelEntityCommand> replyTo,
            ActorRef<ChatChannelReaderCommand> readerRef
    ) {
        Mono.fromCallable(() -> messageRepository.findHistory(channelId, messageSequence, size))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(history -> replyTo.tell(new HistoryFound(history, readerRef)))
            .subscribe();
    }

    @Override
    public void findRecentMessages(Long channelId, int size, ActorRef<ChatChannelEntityCommand> replyTo) {
        Mono.fromCallable(() -> messageRepository.findLatest(channelId, size))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(messages -> replyTo.tell(new ChatChannelProtocol.SyncRecentMessages(messages)))
            .subscribe();
    }
}
