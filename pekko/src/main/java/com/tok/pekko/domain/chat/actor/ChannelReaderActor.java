package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.chat.actor.ChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.GetHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.UnregisterClientSession;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.SpawnedChannelReaderActor;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.Terminated;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

public class ChannelReaderActor extends AbstractBehavior<ChannelReaderCommand> {

    public static Behavior<ChannelReaderCommand> create(
            Long channelId,
            ChatMessages messages,
            EntityRef<ChannelEntityCommand> channelEntity,
            ActorRef<ChannelReaderRegistryCommand> readerRegistry
    ) {
        return Behaviors.setup(
                context -> {
                    channelEntity.tell(new RequestSyncMessages(context.getSelf()));

                    String readerName = context.getSelf().path().address().toString() + "/"
                            + context.getSelf().path().name();

                    readerRegistry.tell(new SpawnedChannelReaderActor(channelId, context.getSelf(), readerName));

                    return Behaviors.withTimers(
                            timers -> {
                                timers.startTimerAtFixedRate(new SyncMessageHeartBeat(), Duration.ofSeconds(30L));

                                return new ChannelReaderActor(
                                        context,
                                        channelId,
                                        messages,
                                        channelEntity
                                );
                            }
                    );
                }
        );
    }

    private final Long channelId;
    private final ChatMessages messages;
    private final EntityRef<ChannelEntityCommand> channelEntity;
    private final Map<Long, ActorRef<ClientSessionCommand>> clientSessions;

    private ChannelReaderActor(
            ActorContext<ChannelReaderCommand> context,
            Long channelId,
            ChatMessages messages,
            EntityRef<ChannelEntityCommand> channelEntity
    ) {
        super(context);

        this.channelId = channelId;
        this.messages = messages;
        this.channelEntity = channelEntity;
        this.clientSessions = new HashMap<>();
    }

    @Override
    public Receive<ChannelReaderCommand> createReceive() {
        return newReceiveBuilder().onMessage(SyncNewMessage.class, this::onSyncNewMessage)
                                  .onMessage(SyncUpdate.class, this::onSyncUpdate)
                                  .onMessage(SyncDeletion.class, this::onSyncDeletion)
                                  .onMessage(SyncMessageHeartBeat.class, this::onSyncMessageHeartBeat)
                                  .onMessage(DeliverSyncMessages.class, this::onDeliverSyncMessages)
                                  .onMessage(GetHistory.class, this::onGetHistory)
                                  .onMessage(RegisterClientSession.class, this::onRegisterClientSession)
                                  .onMessage(UnregisterClientSession.class, this::onUnregisterClientSession)
                                  .onSignal(Terminated.class, this::onTerminated)
                                  .onSignal(PostStop.class, this::onPostStop)
                                  .build();
    }

    private Behavior<ChannelReaderCommand> onSyncNewMessage(SyncNewMessage command) {
        messages.add(command.message());
        clientSessions.values()
                      .forEach(clientSession -> clientSession.tell(new DeliverNewMessage(command.message())));

        return this;
    }

    private Behavior<ChannelReaderCommand> onSyncUpdate(SyncUpdate command) {
        ChatMessage updatedMessage = messages.update(
                command.messageId(),
                command.updatedMessage(),
                command.updatedAt()
        );

        clientSessions.values()
                      .forEach(clientSession -> clientSession.tell(new DeliverUpdatedMessage(updatedMessage)));

        return this;
    }

    private Behavior<ChannelReaderCommand> onSyncDeletion(SyncDeletion command) {
        ChatMessage deletedMessage = messages.delete(command.messageId());

        clientSessions.values()
                      .forEach(clientSession -> clientSession.tell(new DeliverDeletedMessage(deletedMessage)));

        return this;
    }

    private Behavior<ChannelReaderCommand> onGetHistory(GetHistory command) {
        List<ChatMessage> history = this.messages.getHistory(command.messageSequence(), command.size());

        if (history.isEmpty()) {
            channelEntity.tell(new ResolveHistory(command.messageSequence(), command.size(), command.replyTo()));
            return this;
        }

        command.replyTo()
               .tell(new DeliverHistory(channelId, command.messageSequence(), command.size(), history));
        return this;
    }

    private Behavior<ChannelReaderCommand> onSyncMessageHeartBeat(SyncMessageHeartBeat command) {
        channelEntity.tell(new RequestSyncMessages(getContext().getSelf()));

        return this;
    }

    private Behavior<ChannelReaderCommand> onDeliverSyncMessages(DeliverSyncMessages command) {
        this.messages.syncMessages(command.messages());

        return this;
    }

    private Behavior<ChannelReaderCommand> onRegisterClientSession(RegisterClientSession command) {
        clientSessions.put(command.userId(), command.clientSession());
        getContext().watch(command.clientSession());

        return this;
    }

    private Behavior<ChannelReaderCommand> onUnregisterClientSession(UnregisterClientSession command) {
        clientSessions.remove(command.userId());

        return this;
    }

    private Behavior<ChannelReaderCommand> onTerminated(Terminated signal) {
        clientSessions.entrySet()
                      .stream()
                      .filter(entry -> entry.getValue().path().equals(signal.getRef().path()))
                      .map(Map.Entry::getKey)
                      .findFirst()
                      .ifPresent(clientSessions::remove);

        return this;
    }

    private Behavior<ChannelReaderCommand> onPostStop(PostStop signal) {
        clientSessions.clear();

        return this;
    }

    private record SyncMessageHeartBeat() implements ChannelReaderCommand { }
    record DeliverSyncMessages(List<ChatMessage> messages) implements ChannelReaderCommand { }
}
