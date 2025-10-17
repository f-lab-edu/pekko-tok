package com.tok.pekko.infrastructure.persistence.actor;

import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.HistoryLoaded;
import com.tok.pekko.infrastructure.persistence.event.LoadedHistoryEvent;
import com.tok.pekko.infrastructure.persistence.event.StoredEvent;
import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.infrastructure.actor.CborSerializable;
import com.tok.pekko.infrastructure.persistence.actor.MessageStorageActor.MessageStoreCommand;
import com.tok.pekko.infrastructure.persistence.repository.MessageRepository;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.eventstream.EventStream;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class MessageStorageActor extends AbstractBehavior<MessageStoreCommand> {

    private final MessageRepository messageRepository;

    public static Behavior<MessageStoreCommand> create(MessageRepository messageRepository) {
        return Behaviors.setup(
                context -> {
                    subscribeEventStream(context);

                    return new MessageStorageActor(context, messageRepository);
                }
        );
    }

    private static void subscribeEventStream(ActorContext<MessageStoreCommand> context) {
        ActorRef<StoredEvent> storedEventAdapter = context.messageAdapter(
                StoredEvent.class,
                event -> new Store(event.message())
        );
        context.getSystem()
               .eventStream()
               .tell(
                       new EventStream.Subscribe<>(StoredEvent.class, storedEventAdapter)
               );

        ActorRef<LoadedHistoryEvent> loadedHistoryAdapter = context.messageAdapter(
                LoadedHistoryEvent.class,
                event -> new FetchHistory(event.channelId(), event.messageSequence(), event.size(), event.replyTo())
        );
        context.getSystem()
               .eventStream()
               .tell(
                        new EventStream.Subscribe<>(LoadedHistoryEvent.class, loadedHistoryAdapter)
                );
    }

    private MessageStorageActor(ActorContext<MessageStoreCommand> context, MessageRepository messageRepository) {
        super(context);

        this.messageRepository = messageRepository;
    }

    @Override
    public Receive<MessageStoreCommand> createReceive() {
        return newReceiveBuilder().onMessage(Store.class, this::onStore)
                                  .onMessage(FetchHistory.class, this::onFetchHistory)
                                  .build();
    }

    private Behavior<MessageStoreCommand> onStore(Store command) {
        messageRepository.save(command.message());

        return this;
    }

    private Behavior<MessageStoreCommand> onFetchHistory(FetchHistory command) {
        List<ChatMessage> history = messageRepository.findAll(
                command.channelId(),
                command.messageSequence(),
                command.size()
        );

        command.replyTo()
                .tell(new HistoryLoaded(history));

        return this;
    }

    public interface MessageStoreCommand extends CborSerializable { }

    public record Store(ChatMessage message) implements MessageStoreCommand { }
    public record FetchHistory(Long channelId, long messageSequence, int size, ActorRef<ChatChannelReaderCommand> replyTo) implements MessageStoreCommand { }
}
