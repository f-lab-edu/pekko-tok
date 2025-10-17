package com.tok.pekko.domain.chat.model;

import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.HistoryLoaded;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.RequestHistory;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncNewCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class ChatChannelReaderActor extends AbstractBehavior<ChatChannelReaderCommand> {

    private final Long channelId;
    private final ChatMessages messages;
    private final MessageStoragePort messageStoragePort;
    private final ActorRef<ClientSessionProtocol.ClientSessionCommand> clientSession;

    public static Behavior<ChatChannelReaderCommand> create(
            Long channelId,
            ChatMessages messages,
            MessageStoragePort messageStoragePort,
            ActorRef<ClientSessionProtocol.ClientSessionCommand> clientSession
    ) {
        return Behaviors.setup(
                context -> new ChatChannelReaderActor(
                        context,
                        channelId,
                        messages,
                        messageStoragePort,
                        clientSession
                )
        );
    }

    private ChatChannelReaderActor(
            ActorContext<ChatChannelReaderCommand> context,
            Long channelId,
            ChatMessages messages,
            MessageStoragePort messageStoragePort,
            ActorRef<ClientSessionProtocol.ClientSessionCommand> clientSession
    ) {
        super(context);

        this.channelId = channelId;
        this.messages = messages;
        this.messageStoragePort = messageStoragePort;
        this.clientSession = clientSession;
    }

    @Override
    public Receive<ChatChannelReaderCommand> createReceive() {
        return newReceiveBuilder().onMessage(SyncNewCommand.class, this::onSyncNewMessage)
                                  .onMessage(RequestHistory.class, this::onRequestHistory)
                                  .onMessage(HistoryLoaded.class, this::onHistoryLoaded)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .build();
    }

    public Behavior<ChatChannelReaderCommand> onSyncNewMessage(SyncNewCommand command) {
        messages.add(command.message());
        clientSession.tell(new ClientSessionProtocol.DeliverCommand(command.message()));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onRequestHistory(RequestHistory command) {
        List<ChatMessage> history = this.messages.getHistory(command.messageSequence(), command.size());

        if (history.isEmpty()) {
            messageStoragePort.findHistory(
                    channelId,
                    command.messageSequence(),
                    command.size(),
                    getContext().getSelf()
            );
        }

        clientSession.tell(new ClientSessionProtocol.DeliverHistory(history));
        return this;
    }

    private Behavior<ChatChannelReaderCommand> onHistoryLoaded(HistoryLoaded command) {
        clientSession.tell(new ClientSessionProtocol.DeliverHistory(command.history()));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onShutdown(Shutdown command) {
        clientSession.tell(new ClientSessionProtocol.Shutdown());

        return Behaviors.stopped();
    }
}
