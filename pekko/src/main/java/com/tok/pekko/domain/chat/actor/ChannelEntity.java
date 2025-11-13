package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.chat.actor.ChannelReaderActor.DeliverSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.DeleteMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveHistory;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SendMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncDeletedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncRecentMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncUpdatedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.UpdateMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

public class ChannelEntity extends AbstractBehavior<ChannelEntityCommand> {

    public static final EntityTypeKey<ChannelEntityCommand> ENTITY_TYPE_KEY =
            EntityTypeKey.create(ChannelEntityCommand.class, "ChatChannel");
    private static final int DEFAULT_RECENT_MESSAGE_SIZE = 50;

    public static Behavior<ChannelEntityCommand> create(
            Clock clock,
            Long channelId,
            ChatMessages messages,
            MessageStoragePort messageStoragePort
    ) {
        return Behaviors.setup(
                context -> {
                    messageStoragePort.findRecentMessages(channelId, DEFAULT_RECENT_MESSAGE_SIZE, context.getSelf());

                    return new ChannelEntity(
                            context,
                            clock,
                            channelId,
                            messages,
                            messageStoragePort
                    );
                }
        );
    }

    private final Clock clock;
    private final Long channelId;
    private final ChatMessages messages;
    private final MessageStoragePort messageStoragePort;
    private final SnowflakeSequenceGenerator sequenceGenerator;
    private final Map<String, ActorRef<ChannelReaderCommand>> readers;

    private ChannelEntity(
            ActorContext<ChannelEntityCommand> context,
            Clock clock,
            Long channelId,
            ChatMessages messages,
            MessageStoragePort messageStoragePort
    ) {
        super(context);

        this.clock = clock;
        this.channelId = channelId;
        this.messages = messages;
        this.messageStoragePort = messageStoragePort;
        this.sequenceGenerator = new SnowflakeSequenceGenerator(channelId);
        this.readers = new HashMap<>();
    }

    @Override
    public Receive<ChannelEntityCommand> createReceive() {
        return newReceiveBuilder().onMessage(SyncRecentMessages.class, this::onSyncRecentMessages)
                                  .onMessage(RegisterReader.class, this::onRegisterReader)
                                  .onMessage(SendMessage.class, this::onSendMessage)
                                  .onMessage(UpdateMessage.class, this::onUpdateMessage)
                                  .onMessage(DeleteMessage.class, this::onDeleteMessage)
                                  .onMessage(SyncPersistedMessage.class, this::onSyncPersistedMessage)
                                  .onMessage(SyncUpdatedMessage.class, this::onSyncUpdatedMessage)
                                  .onMessage(SyncDeletedMessage.class, this::onSyncDeletedMessage)
                                  .onMessage(RemoveShutdownReader.class, this::onRemoveShutdownReader)
                                  .onMessage(RequestSyncMessages.class, this::onRequestSyncMessages)
                                  .onMessage(ResolveHistory.class, this::onResolveHistory)
                                  .build();
    }

    private Behavior<ChannelEntityCommand> onSyncRecentMessages(SyncRecentMessages command) {
        this.messages.syncMessages(command.messages());

        return this;
    }

    private Behavior<ChannelEntityCommand> onRegisterReader(RegisterReader command) {
        readers.put(command.readerName(), command.reader());

        return this;
    }

    private Behavior<ChannelEntityCommand> onSendMessage(SendMessage command) {
        ChatMessage message = createChatMessage(command);

        messageStoragePort.store(message, getContext().getSelf());

        return this;
    }

    private Behavior<ChannelEntityCommand> onUpdateMessage(UpdateMessage command) {
        messageStoragePort.update(command.messageId(), command.updatedMessage(), getContext().getSelf());

        return this;
    }

    private Behavior<ChannelEntityCommand> onSyncUpdatedMessage(SyncUpdatedMessage command) {
        LocalDateTime updatedTimestamp = LocalDateTime.now(clock);

        messages.update(command.messageId(), command.updatedMessage(), updatedTimestamp);
        readers.values()
                .forEach(
                        reader -> reader.tell(
                                new SyncUpdate(command.messageId(),command.updatedMessage(), updatedTimestamp)
                        )
                );
        return this;
    }

    private Behavior<ChannelEntityCommand> onDeleteMessage(DeleteMessage command) {
        messageStoragePort.delete(command.messageId(), getContext().getSelf());

        return this;
    }

    private Behavior<ChannelEntityCommand> onSyncDeletedMessage(SyncDeletedMessage command) {
        messages.delete(command.messageId());
        readers.values()
               .forEach(reader -> reader.tell(new SyncDeletion(command.messageId())));

        return this;
    }

    private Behavior<ChannelEntityCommand> onSyncPersistedMessage(SyncPersistedMessage command) {
        messages.add(command.message());
        readers.values()
               .forEach(reader -> reader.tell(new SyncNewMessage(command.message())));

        return this;
    }

    private Behavior<ChannelEntityCommand> onRemoveShutdownReader(RemoveShutdownReader command) {
        readers.remove(command.readerName());

        return this;
    }

    private Behavior<ChannelEntityCommand> onRequestSyncMessages(RequestSyncMessages command) {
        command.secondary()
               .tell(new DeliverSyncMessages(messages.getMessages()));

        return this;
    }

    private Behavior<ChannelEntityCommand> onResolveHistory(ResolveHistory command) {
        List<ChatMessage> history = this.messages.getHistory(command.messageSequence(), command.size());

        command.replyTo()
               .tell(new DeliverHistory(channelId, command.messageSequence(), command.size(), history));
        return this;
    }

    private ChatMessage createChatMessage(SendMessage command) {
        long messageSequence = sequenceGenerator.nextSequence();

        return ChatMessage.create(
                channelId,
                command.userId(),
                messageSequence,
                command.message(),
                LocalDateTime.now(clock),
                LocalDateTime.now(clock)
        );
    }

    // 채팅 히스토리 동기화를 요청하는 메시지 : ChannelReaderActor -> ChannelEntity
    record RequestSyncMessages(ActorRef<ChannelReaderCommand> secondary) implements ChannelEntityCommand { }
}
