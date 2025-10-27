package com.tok.pekko.domain.chat.model;

import com.tok.pekko.domain.chat.model.ChatChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.RequestHistory;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncNewCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import java.time.Duration;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

public class ChatChannelReaderActor extends AbstractBehavior<ChatChannelReaderCommand> {

    public static Behavior<ChatChannelReaderCommand> create(
            ChatMessages messages,
            EntityRef<ChatChannelEntityCommand> channelEntity,
            ActorRef<ClientSessionCommand> clientSession
    ) {
        return Behaviors.setup(
                context -> {
                    channelEntity.tell(new RequestSyncMessages(context.getSelf()));

                    return Behaviors.withTimers(timers ->
                            new ChatChannelReaderActor(context, messages, timers, channelEntity, clientSession)
                    );
                }
        );
    }

    private final ChatMessages messages;
    private final EntityRef<ChatChannelEntityCommand> channelEntity;
    private final ActorRef<ClientSessionCommand> clientSession;

    private ChatChannelReaderActor(
            ActorContext<ChatChannelReaderCommand> context,
            ChatMessages messages,
            TimerScheduler<ChatChannelReaderCommand> timers,
            EntityRef<ChatChannelEntityCommand> channelEntity,
            ActorRef<ClientSessionCommand> clientSession
    ) {
        super(context);

        this.messages = messages;
        this.channelEntity = channelEntity;
        this.clientSession = clientSession;

        timers.startTimerAtFixedRate("sync-heartbeat", new HeartBeat(), Duration.ofSeconds(30));
    }

    @Override
    public Receive<ChatChannelReaderCommand> createReceive() {
        return newReceiveBuilder().onMessage(SyncNewCommand.class, this::onSyncNewMessage)
                                  .onMessage(SyncUpdate.class, this::onSyncUpdate)
                                  .onMessage(SyncDeletion.class, this::onSyncDeletion)
                                  .onMessage(RequestHistory.class, this::onRequestHistory)
                                  .onMessage(HeartBeat.class, this::onHeartBeat)
                                  .onMessage(DeliverSyncMessages.class, this::onDeliverSyncMessages)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .build();
    }

    private Behavior<ChatChannelReaderCommand> onSyncNewMessage(SyncNewCommand command) {
        messages.add(command.message());
        clientSession.tell(new DeliverCommand(command.message()));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onSyncUpdate(SyncUpdate command) {
        ChatMessage updatedMessage = messages.update(command.messageId(), command.updatedMessage());

        clientSession.tell(new DeliverUpdatedMessage(updatedMessage));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onSyncDeletion(SyncDeletion command) {
        ChatMessage deletedMessage = messages.delete(command.messageId());

        clientSession.tell(new DeliverDeletedMessage(deletedMessage));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onRequestHistory(RequestHistory command) {
        List<ChatMessage> history = this.messages.getHistory(command.messageSequence(), command.size());

        clientSession.tell(new DeliverHistory(history));
        return this;
    }

    private Behavior<ChatChannelReaderCommand> onShutdown(Shutdown command) {
        clientSession.tell(new ClientSessionProtocol.Shutdown());

        return Behaviors.stopped();
    }

    private Behavior<ChatChannelReaderCommand> onHeartBeat(HeartBeat command) {
        channelEntity.tell(new RequestSyncMessages(getContext().getSelf()));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onDeliverSyncMessages(DeliverSyncMessages command) {
        this.messages.syncMessages(command.messages());

        return this;
    }

    private record HeartBeat() implements ChatChannelReaderCommand { }
    record DeliverSyncMessages(List<ChatMessage> messages) implements ChatChannelReaderCommand { }
}
