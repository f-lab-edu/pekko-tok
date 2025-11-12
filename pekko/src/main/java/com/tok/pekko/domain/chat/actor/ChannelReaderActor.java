package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.chat.actor.ChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.GetHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RequestInitialHistory;
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
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundHistory;
import java.time.Duration;
import java.util.ArrayList;
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
    private final List<RequestInitialHistory> requestInitialHistories;
    private final List<Runnable> pendingSyncEvents;
    private boolean initialHistorySynced;

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
        this.requestInitialHistories = new ArrayList<>();
        this.pendingSyncEvents = new ArrayList<>();
        this.initialHistorySynced = false;
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
                                  .onMessage(RequestInitialHistory.class, this::onRequestInitialHistory)
                                  .onSignal(Terminated.class, this::onTerminated)
                                  .onSignal(PostStop.class, this::onPostStop)
                                  .build();
    }

    private Behavior<ChannelReaderCommand> onSyncNewMessage(SyncNewMessage command) {
        if (!initialHistorySynced) {
            pendingSyncEvents.add(() -> applySyncNewMessage(command));
            return this;
        }

        applySyncNewMessage(command);

        return this;
    }

    private Behavior<ChannelReaderCommand> onSyncUpdate(SyncUpdate command) {
        if (!initialHistorySynced) {
            pendingSyncEvents.add(() -> applySyncUpdate(command));
            return this;
        }

        applySyncUpdate(command);

        return this;
    }

    private Behavior<ChannelReaderCommand> onSyncDeletion(SyncDeletion command) {
        if (!initialHistorySynced) {
            pendingSyncEvents.add(() -> applySyncDeletion(command));
            return this;
        }

        applySyncDeletion(command);

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
        messages.syncMessages(command.messages());
        initialHistorySynced = true;
        applyPendingSyncEvents();
        fulfillPendingInitialHistoryRequests();

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

    private Behavior<ChannelReaderCommand> onRequestInitialHistory(RequestInitialHistory command) {
        if (!initialHistorySynced) {
            requestInitialHistories.add(command);
            return this;
        }

        List<ChatMessage> history = this.messages.getMessages();

        command.replyTo()
                .tell(new FoundHistory(history));
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

    private void applySyncNewMessage(SyncNewMessage command) {
        messages.add(command.message());
        clientSessions.values()
                      .forEach(clientSession -> clientSession.tell(new DeliverNewMessage(command.message())));
    }

    private void applySyncUpdate(SyncUpdate command) {
        ChatMessage updatedMessage = messages.update(
                command.messageId(),
                command.updatedMessage(),
                command.updatedAt()
        );

        clientSessions.values()
                      .forEach(clientSession -> clientSession.tell(new DeliverUpdatedMessage(updatedMessage)));
    }

    private void applySyncDeletion(SyncDeletion command) {
        ChatMessage deletedMessage = messages.delete(command.messageId());

        clientSessions.values()
                      .forEach(clientSession -> clientSession.tell(new DeliverDeletedMessage(deletedMessage)));
    }

    private void fulfillPendingInitialHistoryRequests() {
        if (requestInitialHistories.isEmpty()) {
            return;
        }

        List<ChatMessage> historySnapshot = messages.getMessages();

        requestInitialHistories.forEach(
                requestInitialHistory -> requestInitialHistory.replyTo().tell(new FoundHistory(historySnapshot))
        );
        requestInitialHistories.clear();
    }

    private void applyPendingSyncEvents() {
        if (pendingSyncEvents.isEmpty()) {
            return;
        }

        pendingSyncEvents.forEach(Runnable::run);
        pendingSyncEvents.clear();
    }

    // 30초 간격으로 Primary인 ChannelEntity에 RequestSyncMessages를 보내도록 트리거하는 내부 타이머 메시지
    private record SyncMessageHeartBeat() implements ChannelReaderCommand { }

    // Primary가 동기화한 채팅 히스토리를 전달받는 메시지
    record DeliverSyncMessages(List<ChatMessage> messages) implements ChannelReaderCommand { }
}
