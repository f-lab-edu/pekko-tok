package com.tok.pekko.domain.chat.model;

import com.tok.pekko.domain.chat.model.ChatChannelReaderActor.DeliverSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RequestJoin;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SendMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncRecentMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncNewCommand;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.domain.chat.port.out.NodeManagerProtocol.CreateReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

public class ChatChannelEntity extends AbstractBehavior<ChatChannelEntityCommand> {

    public static final EntityTypeKey<ChatChannelEntityCommand> ENTITY_TYPE_KEY =
            EntityTypeKey.create(ChatChannelEntityCommand.class, "ChatChannel");
    private static final int DEFAULT_RECENT_MESSAGE_SIZE = 50;

    public static Behavior<ChatChannelEntityCommand> create(
            Long channelId,
            ChatMessages messages,
            MessageStoragePort messageStoragePort
    ) {
        return Behaviors.setup(
                context -> {
                    messageStoragePort.findRecentMessages(channelId, DEFAULT_RECENT_MESSAGE_SIZE, context.getSelf());

                    return new ChatChannelEntity(
                            context,
                            channelId,
                            messages,
                            messageStoragePort,
                            new SnowflakeSequenceGenerator(channelId),
                            new HashMap<>()
                    );
                }
        );
    }

    private final Long channelId;
    private final ChatMessages messages;
    private final MessageStoragePort messageStoragePort;
    private final SnowflakeSequenceGenerator sequenceGenerator;
    private final Map<ChannelReaderKey, ActorRef<ChatChannelReaderCommand>> readers;

    private ChatChannelEntity(
            ActorContext<ChatChannelEntityCommand> context,
            Long channelId,
            ChatMessages messages,
            MessageStoragePort messageStoragePort,
            SnowflakeSequenceGenerator sequenceGenerator,
            Map<ChannelReaderKey, ActorRef<ChatChannelReaderCommand>> readers
    ) {
        super(context);

        this.channelId = channelId;
        this.messages = messages;
        this.messageStoragePort = messageStoragePort;
        this.sequenceGenerator = sequenceGenerator;
        this.readers = readers;
    }

    @Override
    public Receive<ChatChannelEntityCommand> createReceive() {
        return newReceiveBuilder().onMessage(SyncRecentMessages.class, this::onSyncRecentMessages)
                                  .onMessage(RequestJoin.class, this::onRequestJoin)
                                  .onMessage(RegisterReader.class, this::onRegisterReader)
                                  .onMessage(SendMessage.class, this::onSendMessage)
                                  .onMessage(SyncPersistedMessage.class, this::onSyncPersistedMessage)
                                  .onMessage(RemoveShutdownReader.class, this::onRemoveShutdownReader).onMessage(
                        RequestSyncMessages.class, this::onRequestSyncMessages)
                                  .build();
    }

    private Behavior<ChatChannelEntityCommand> onSyncRecentMessages(SyncRecentMessages command) {
        this.messages.syncMessages(command.messages());

        return this;
    }

    private Behavior<ChatChannelEntityCommand> onRequestJoin(RequestJoin command) {
        command.replyTo()
               .tell(
                       new CreateReader(
                               messages.deepCopy(),
                               command.clientRef(),
                               channelId,
                               command.userId(),
                               getContext().getSelf()
                       )
               );

        return this;
    }

    private Behavior<ChatChannelEntityCommand> onRegisterReader(RegisterReader command) {
        ChannelReaderKey key = new ChannelReaderKey(command.userId());
        ActorRef<ChatChannelReaderCommand> oldReader = readers.put(key, command.reader());

        if (oldReader != null) {
            oldReader.tell(new Shutdown());
        }

        return this;
    }

    private Behavior<ChatChannelEntityCommand> onSendMessage(SendMessage command) {
        ChatMessage message = createChatMessage(command);

        messageStoragePort.store(message, getContext().getSelf());

        return this;
    }

    private Behavior<ChatChannelEntityCommand> onSyncPersistedMessage(SyncPersistedMessage command) {
        messages.add(command.message());
        readers.values()
               .forEach(reader -> reader.tell(new SyncNewCommand(command.message())));

        return this;
    }

    private Behavior<ChatChannelEntityCommand> onRemoveShutdownReader(RemoveShutdownReader command) {
        ChannelReaderKey channelReaderKey = new ChannelReaderKey(command.userId());

        readers.remove(channelReaderKey);

        return this;
    }

    private Behavior<ChatChannelEntityCommand> onRequestSyncMessages(RequestSyncMessages command) {
        command.secondary()
               .tell(new DeliverSyncMessages(messages.getMessages()));

        return this;
    }

    private ChatMessage createChatMessage(SendMessage command) {
        long messageSequence = sequenceGenerator.nextSequence();

        return ChatMessage.create(
                channelId,
                command.userId(),
                messageSequence,
                command.message(),
                command.timestamp()
        );
    }

    record RequestSyncMessages(ActorRef<ChatChannelReaderCommand> secondary) implements ChatChannelEntityCommand { }
}
