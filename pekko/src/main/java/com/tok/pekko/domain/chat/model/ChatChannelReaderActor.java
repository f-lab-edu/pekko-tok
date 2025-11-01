package com.tok.pekko.domain.chat.model;

import com.tok.pekko.domain.chat.model.ChatChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ResolveHistory;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.PingHealthCheckFromRegistry;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.PingHealthCheckFromClientSession;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.PongHealthCheck;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.GetHistory;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.UnregisterClientSession;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.SpawnedChannelReaderActor;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PingHealthCheck;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Terminated;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

public class ChatChannelReaderActor extends AbstractBehavior<ChatChannelReaderCommand> {

    public static Behavior<ChatChannelReaderCommand> create(
            Long channelId,
            ChatMessages messages,
            EntityRef<ChatChannelEntityCommand> channelEntity,
            ActorRef<ChannelReaderRegistryCommand> replyTo
    ) {
        return Behaviors.setup(
                context -> {
                    channelEntity.tell(new RequestSyncMessages(context.getSelf()));

                    String readerName = context.getSelf().path().address().toString() + "/"
                            + context.getSelf().path().name();

                    replyTo.tell(new SpawnedChannelReaderActor(channelId, context.getSelf(), readerName));

                    return Behaviors.withTimers(
                            timers -> {
                                timers.startTimerAtFixedRate(new SyncMessageHeartBeat(), Duration.ofSeconds(30L));
                                timers.startTimerAtFixedRate(new ClientSessionHealthCheckHeartBeat(), Duration.ofSeconds(60L));

                                return new ChatChannelReaderActor(
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
    private final EntityRef<ChatChannelEntityCommand> channelEntity;
    private final Map<Long, ActorRef<ClientSessionCommand>> clientSessions;
    private final Map<Long, Cancellable> healthCheckTimeouts;

    private ChatChannelReaderActor(
            ActorContext<ChatChannelReaderCommand> context,
            Long channelId,
            ChatMessages messages,
            EntityRef<ChatChannelEntityCommand> channelEntity
    ) {
        super(context);

        this.channelId = channelId;
        this.messages = messages;
        this.channelEntity = channelEntity;
        this.clientSessions = new HashMap<>();
        this.healthCheckTimeouts = new HashMap<>();
    }

    @Override
    public Receive<ChatChannelReaderCommand> createReceive() {
        return newReceiveBuilder().onMessage(SyncNewMessage.class, this::onSyncNewMessage)
                                  .onMessage(SyncUpdate.class, this::onSyncUpdate)
                                  .onMessage(SyncDeletion.class, this::onSyncDeletion)
                                  .onMessage(SyncMessageHeartBeat.class, this::onHeartBeat)
                                  .onMessage(DeliverSyncMessages.class, this::onDeliverSyncMessages)
                                  .onMessage(GetHistory.class, this::onGetHistory)
                                  .onMessage(PingHealthCheckFromRegistry.class, this::onPingHealthCheckFromRegistry)
                                  .onMessage(PingHealthCheckFromClientSession.class, this::onPingHealthCheckFromClientSession)
                                  .onMessage(ClientSessionHealthCheckHeartBeat.class, this::onClientSessionHealthCheckHeartBeat)
                                  .onMessage(PongHealthCheck.class, this::onPongHealthCheck)
                                  .onMessage(PongHealthCheckTimeout.class, this::onPongHealthCheckTimeout)
                                  .onMessage(RegisterClientSession.class, this::onRegisterClientSession)
                                  .onMessage(UnregisterClientSession.class, this::onUnregisterClientSession)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .onSignal(Terminated.class, this::onTerminated)
                                  .build();
    }

    private Behavior<ChatChannelReaderCommand> onSyncNewMessage(SyncNewMessage command) {
        messages.add(command.message());
        clientSessions.values()
                      .forEach(clientSession -> clientSession.tell(new DeliverNewMessage(command.message())));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onSyncUpdate(SyncUpdate command) {
        ChatMessage updatedMessage = messages.update(
                command.messageId(),
                command.updatedMessage(),
                command.updatedAt()
        );

        clientSessions.values()
                      .forEach(clientSession -> clientSession.tell(new DeliverUpdatedMessage(updatedMessage)));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onSyncDeletion(SyncDeletion command) {
        ChatMessage deletedMessage = messages.delete(command.messageId());

        clientSessions.values()
                      .forEach(clientSession -> clientSession.tell(new DeliverDeletedMessage(deletedMessage)));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onGetHistory(GetHistory command) {
        List<ChatMessage> history = this.messages.getHistory(command.messageSequence(), command.size());

        if (history.isEmpty()) {
            channelEntity.tell(new ResolveHistory(command.messageSequence(), command.size(), command.replyTo()));
            return this;
        }

        command.replyTo()
               .tell(new DeliverHistory(channelId, command.messageSequence(), command.size(), history));
        return this;
    }

    private Behavior<ChatChannelReaderCommand> onHeartBeat(SyncMessageHeartBeat command) {
        channelEntity.tell(new RequestSyncMessages(getContext().getSelf()));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onDeliverSyncMessages(DeliverSyncMessages command) {
        this.messages.syncMessages(command.messages());

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onPingHealthCheckFromRegistry(PingHealthCheckFromRegistry command) {
        command.replyTo()
               .tell(new ChannelReaderRegistryProtocol.PongHealthCheck(channelId));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onPingHealthCheckFromClientSession(PingHealthCheckFromClientSession command) {
        command.replyTo()
               .tell(new ClientSessionProtocol.PongHealthCheck(channelId));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onRegisterClientSession(RegisterClientSession command) {
        clientSessions.put(command.userId(), command.clientSession());
        getContext().watch(command.clientSession());

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onClientSessionHealthCheckHeartBeat(
            ClientSessionHealthCheckHeartBeat command
    ) {
        if (clientSessions.isEmpty()) {
            return this;
        }

        for (Entry<Long, ActorRef<ClientSessionCommand>> clientSessionEntry : clientSessions.entrySet()) {
            Long userId = clientSessionEntry.getKey();
            ActorRef<ClientSessionCommand> clientSession = clientSessionEntry.getValue();

            clientSession.tell(new PingHealthCheck(getContext().getSelf()));

            Cancellable timeoutSchedule = getContext().scheduleOnce(
                    Duration.ofSeconds(60L),
                    getContext().getSelf(),
                    new PongHealthCheckTimeout(userId)
            );

            Cancellable oldSchedule = healthCheckTimeouts.put(userId, timeoutSchedule);

            if (oldSchedule != null) {
                oldSchedule.cancel();
            }
        }

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onPongHealthCheck(PongHealthCheck command) {
        Cancellable schedule = healthCheckTimeouts.remove(command.userId());

        if (schedule != null) {
            schedule.cancel();
        }

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onPongHealthCheckTimeout(PongHealthCheckTimeout command) {
        healthCheckTimeouts.remove(command.userId());

        ActorRef<ClientSessionCommand> clientSession = clientSessions.remove(command.userId());

        if (clientSession != null) {
            getContext().unwatch(clientSession);
        }

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onTerminated(Terminated signal) {
        clientSessions.entrySet()
                      .stream()
                      .filter(entry -> entry.getValue().path().equals(signal.getRef().path()))
                      .map(Map.Entry::getKey)
                      .findFirst()
                      .ifPresent(userId -> {
                                  healthCheckTimeouts.remove(userId);
                                  clientSessions.remove(userId);
                              });

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onUnregisterClientSession(UnregisterClientSession command) {
        clientSessions.remove(command.userId());

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onShutdown(Shutdown command) {
        return Behaviors.stopped();
    }

    private record SyncMessageHeartBeat() implements ChatChannelReaderCommand { }
    private record ClientSessionHealthCheckHeartBeat() implements ChatChannelReaderCommand { }
    private record PongHealthCheckTimeout(Long userId) implements ChatChannelReaderCommand { }
    record DeliverSyncMessages(List<ChatMessage> messages) implements ChatChannelReaderCommand { }
}
