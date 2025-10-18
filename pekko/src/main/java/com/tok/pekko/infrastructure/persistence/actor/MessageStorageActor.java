package com.tok.pekko.infrastructure.persistence.actor;

import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.HistoryLoaded;
import com.tok.pekko.infrastructure.persistence.event.LoadedHistoryEvent;
import com.tok.pekko.infrastructure.persistence.event.LoadedRecentMessagesEvent;
import com.tok.pekko.infrastructure.persistence.event.StoredEvent;
import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.common.CborSerializable;
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
        subscribeStoreEvent(context);
        subscribeLoadedHistoryEvent(context);
        subscribeLoadedRecentMessagesEvent(context);
    }

    private static void subscribeStoreEvent(ActorContext<MessageStoreCommand> context) {
        ActorRef<StoredEvent> storedEventAdapter = context.messageAdapter(
                StoredEvent.class,
                event -> new Store(event.message(), event.replyTo())
        );

        context.getSystem()
               .eventStream()
               .tell(
                       new EventStream.Subscribe<>(StoredEvent.class, storedEventAdapter)
               );
    }

    private static void subscribeLoadedHistoryEvent(ActorContext<MessageStoreCommand> context) {
        ActorRef<LoadedHistoryEvent> loadedHistoryAdapter = context.messageAdapter(
                LoadedHistoryEvent.class,
                event -> new FetchHistory(event.channelId(), event.messageSequence(), event.size(), event.replyTo())
        );

        context.getSystem()
               .eventStream()
               .tell(new EventStream.Subscribe<>(LoadedHistoryEvent.class, loadedHistoryAdapter));
    }

    private static void subscribeLoadedRecentMessagesEvent(ActorContext<MessageStoreCommand> context) {
        ActorRef<LoadedRecentMessagesEvent> loadedRecentMessagesAdapter = context.messageAdapter(
                LoadedRecentMessagesEvent.class,
                event -> new FetchRecentMessages(event.channelId(), event.size(), event.replyTo())
        );

        context.getSystem()
               .eventStream()
               .tell(new EventStream.Subscribe<>(LoadedRecentMessagesEvent.class, loadedRecentMessagesAdapter));
    }

    private MessageStorageActor(ActorContext<MessageStoreCommand> context, MessageRepository messageRepository) {
        super(context);

        this.messageRepository = messageRepository;
    }

    @Override
    public Receive<MessageStoreCommand> createReceive() {
        return newReceiveBuilder().onMessage(Store.class, this::onStore)
                                  .onMessage(FetchHistory.class, this::onFetchHistory)
                                  .onMessage(FetchRecentMessages.class, this::onFetchRecentMessage)
                                  .build();
    }

    private Behavior<MessageStoreCommand> onStore(Store command) {
        ChatMessage persistedMessage = messageRepository.save(command.message());

        command.replyTo()
               .tell(new SyncPersistedMessage(persistedMessage));

        return this;
    }

    private Behavior<MessageStoreCommand> onFetchHistory(FetchHistory command) {
        List<ChatMessage> history = messageRepository.findHistory(
                command.channelId(),
                command.messageSequence(),
                command.size()
        );

        command.replyTo()
               .tell(new HistoryLoaded(history));

        return this;
    }

    private Behavior<MessageStoreCommand> onFetchRecentMessage(FetchRecentMessages command) {
        List<ChatMessage> messages = messageRepository.findLatest(command.channelId(), command.size());

        if (!messages.isEmpty()) {
            command.replyTo()
                   .tell(new ChatChannelProtocol.SyncRecentMessages(messages));
        }

        return this;
    }

    public interface MessageStoreCommand extends CborSerializable {

    }

    public record Store(ChatMessage message, ActorRef<ChatChannelEntityCommand> replyTo) implements
            MessageStoreCommand {

    }

    public record FetchHistory(Long channelId, long messageSequence, int size,
                               ActorRef<ChatChannelReaderCommand> replyTo) implements MessageStoreCommand {

    }

    public record FetchRecentMessages(Long channelId, int size, ActorRef<ChatChannelEntityCommand> replyTo) implements
            MessageStoreCommand {

    }
}
