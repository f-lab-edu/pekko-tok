package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ChannelReaderRegistryActor.GetChannelReaderActor;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.GetHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RequestInitialHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.UnregisterClientSession;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ReleaseChannelReaderActor;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundRegisteredChannelIds;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.RequestHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncJoinChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncLeaveChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.RefreshChannelReader;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class ClientSessionActor extends AbstractBehavior<ClientSessionCommand> {

    public static Behavior<ClientSessionCommand> create(
            Long userId,
            ClientMessageSender clientMessageSender,
            MessageStoragePort messageStoragePort,
            ChannelMembershipPort channelMembershipPort,
            ActorRef<ChannelReaderRegistryCommand> readerRegistry
    ) {
        return Behaviors.setup(
                context -> {
                    channelMembershipPort.findParticipatingChannels(userId, context.getSelf());

                    return new ClientSessionActor(
                            context,
                            userId,
                            clientMessageSender,
                            messageStoragePort,
                            readerRegistry
                    );
                }
        );
    }

    private final Long userId;
    private final ClientMessageSender clientMessageSender;
    private final MessageStoragePort messageStoragePort;
    private final ActorRef<ChannelReaderRegistryCommand> readerRegistry;
    private final Map<Long, ActorRef<ChannelReaderCommand>> readers;
    private final Map<Long, RequestHistory> pendingRequestHistory;

    private ClientSessionActor(
            ActorContext<ClientSessionCommand> context,
            Long userId,
            ClientMessageSender clientMessageSender,
            MessageStoragePort messageStoragePort,
            ActorRef<ChannelReaderRegistryCommand> readerRegistry
    ) {
        super(context);

        this.userId = userId;
        this.messageStoragePort = messageStoragePort;
        this.clientMessageSender = clientMessageSender;
        this.readerRegistry = readerRegistry;
        this.readers = new HashMap<>();
        this.pendingRequestHistory = new HashMap<>();
    }

    @Override
    public Receive<ClientSessionCommand> createReceive() {
        return newReceiveBuilder().onMessage(DeliverNewMessage.class, this::onDeliverNewMessage)
                                  .onMessage(DeliverUpdatedMessage.class, this::onDeliverUpdatedMessage)
                                  .onMessage(DeliverDeletedMessage.class, this::onDeliverDeletedMessage)
                                  .onMessage(RequestHistory.class, this::onRequestHistory)
                                  .onMessage(DeliverHistory.class, this::onDeliverHistory)
                                  .onMessage(FoundHistory.class, this::onFoundHistory)
                                  .onMessage(FoundRegisteredChannelIds.class, this::onFoundRegisteredChannelIds)
                                  .onMessage(RefreshChannelReader.class, this::onRefreshChannelReader)
                                  .onMessage(FoundChannelReaders.class, this::onFoundChannelReaders)
                                  .onMessage(SyncJoinChannel.class, this::onSyncJoinChannel)
                                  .onMessage(SyncLeaveChannel.class, this::onSyncLeaveChannel)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .build();
    }

    private Behavior<ClientSessionCommand> onDeliverNewMessage(DeliverNewMessage command) {
        clientMessageSender.sendMessage(command.message());

        return this;
    }

    private Behavior<ClientSessionCommand> onDeliverUpdatedMessage(DeliverUpdatedMessage command) {
        clientMessageSender.sendMessage(command.updatedMessage());

        return this;
    }

    private Behavior<ClientSessionCommand> onDeliverDeletedMessage(DeliverDeletedMessage command) {
        clientMessageSender.sendDeletedMessage(command.deletedMessage());

        return this;
    }

    private Behavior<ClientSessionCommand> onRequestHistory(RequestHistory command) {
        ActorRef<ChannelReaderCommand> reader = readers.get(command.channelId());

        if (reader == null) {
            readerRegistry.tell(new GetChannelReaderActor(userId, List.of(command.channelId()), getContext().getSelf()));
            pendingRequestHistory.put(command.channelId(), command);
            return this;
        }

        reader.tell(new GetHistory(command.messageSequence(), command.size(), getContext().getSelf()));

        return this;
    }

    private Behavior<ClientSessionCommand> onDeliverHistory(DeliverHistory command) {
        if (command.history().isEmpty()) {
            messageStoragePort.findHistory(
                    command.channelId(),
                    command.messageSequence(),
                    command.size(),
                    getContext().getSelf()
            );

            return this;
        }

        clientMessageSender.sendMessages(command.history());

        return this;
    }

    private Behavior<ClientSessionCommand> onFoundHistory(FoundHistory command) {
        clientMessageSender.sendMessages(command.history());

        return this;
    }

    private Behavior<ClientSessionCommand> onFoundRegisteredChannelIds(FoundRegisteredChannelIds command) {
        readerRegistry.tell(new GetChannelReaderActor(userId, command.channelIds(), getContext().getSelf()));

        return this;
    }

    private Behavior<ClientSessionCommand> onRefreshChannelReader(RefreshChannelReader command) {
        readers.remove(command.channelId());
        readerRegistry.tell(new GetChannelReaderActor(userId, List.of(command.channelId()), getContext().getSelf()));
        return this;
    }

    private Behavior<ClientSessionCommand> onFoundChannelReaders(FoundChannelReaders command) {
        for (Entry<Long, ActorRef<ChannelReaderCommand>> readerEntry : command.chatChannelReaderRefs().entrySet()) {
            Long channelId = readerEntry.getKey();
            ActorRef<ChannelReaderCommand> reader = readerEntry.getValue();

            readers.put(channelId, reader);
            reader.tell(new RegisterClientSession(userId, getContext().getSelf()));
            reader.tell(new RequestInitialHistory(getContext().getSelf()));

            RequestHistory requestHistoryCommand = this.pendingRequestHistory.remove(channelId);

            if (requestHistoryCommand != null) {
                reader.tell(
                        new GetHistory(
                                requestHistoryCommand.messageSequence(),
                                requestHistoryCommand.size(),
                                getContext().getSelf()
                        )
                );
            }
        }

        return this;
    }

    private Behavior<ClientSessionCommand> onSyncJoinChannel(SyncJoinChannel command) {
        readerRegistry.tell(new GetChannelReaderActor(userId, List.of(command.channelId()), getContext().getSelf()));
        return this;
    }

    private Behavior<ClientSessionCommand> onSyncLeaveChannel(SyncLeaveChannel command) {
        ActorRef<ChannelReaderCommand> leaveReaderChannel = readers.remove(command.channelId());

        if (leaveReaderChannel != null) {
            leaveReaderChannel.tell(new UnregisterClientSession(userId));
        }

        return this;
    }

    private Behavior<ClientSessionCommand> onShutdown(Shutdown command) {
        ArrayList<Long> channelIds = new ArrayList<>(readers.keySet());

        readerRegistry.tell(new ReleaseChannelReaderActor(userId, channelIds));
        readers.values()
               .forEach(reader -> reader.tell(new UnregisterClientSession(userId)));
        readers.clear();
        pendingRequestHistory.clear();

        return Behaviors.stopped();
    }

    public record FoundChannelReaders(Map<Long, ActorRef<ChannelReaderCommand>> chatChannelReaderRefs) implements ClientSessionCommand { }
}
