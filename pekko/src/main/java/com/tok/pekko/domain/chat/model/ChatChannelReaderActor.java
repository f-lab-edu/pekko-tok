package com.tok.pekko.domain.chat.model;

import com.tok.pekko.domain.chat.model.ChatChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.PingHealthCheckFromRegistry;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.PingHealthCheckFromClientSession;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.RequestHistory;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.UnregisterClientSession;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
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
            ActorRef<ClientSessionCommand> clientSession
    ) {
        return Behaviors.setup(
                context -> {
                    channelEntity.tell(new RequestSyncMessages(context.getSelf()));

                    return Behaviors.withTimers(
                            timers -> {
                                timers.startTimerAtFixedRate(new HeartBeat(), Duration.ofSeconds(30));

                                return new ChatChannelReaderActor(
                                        context,
                                        channelId,
                                        messages,
                                        channelEntity,
                                        clientSession
                                );
                            }
                    );
                }
        );
    }

    private final Long channelId;
    private final ChatMessages messages;
    private final EntityRef<ChatChannelEntityCommand> channelEntity;
    private final ActorRef<ClientSessionCommand> clientSession;
    private final Map<Long, ActorRef<ClientSessionCommand>> clientSessions;

    private ChatChannelReaderActor(
            ActorContext<ChatChannelReaderCommand> context,
            Long channelId,
            ChatMessages messages,
            EntityRef<ChatChannelEntityCommand> channelEntity,
            ActorRef<ClientSessionCommand> clientSession
    ) {
        super(context);

        this.channelId = channelId;
        this.messages = messages;
        this.channelEntity = channelEntity;
        this.clientSession = clientSession;
        this.clientSessions = new HashMap<>();
    }

    @Override
    public Receive<ChatChannelReaderCommand> createReceive() {
        return newReceiveBuilder().onMessage(SyncNewMessage.class, this::onSyncNewMessage)
                                  .onMessage(SyncUpdate.class, this::onSyncUpdate)
                                  .onMessage(SyncDeletion.class, this::onSyncDeletion)
                                  .onMessage(RequestHistory.class, this::onRequestHistory)
                                  .onMessage(HeartBeat.class, this::onHeartBeat)
                                  .onMessage(DeliverSyncMessages.class, this::onDeliverSyncMessages)
                                  .onMessage(PingHealthCheckFromRegistry.class, this::onPingHealthCheckFromRegistry)
                                  .onMessage(PingHealthCheckFromClientSession.class, this::onPingHealthCheckFromClientSession)
                                  .onMessage(RegisterClientSession.class, this::onRegisterClientSession)
                                  .onMessage(UnregisterClientSession.class, this::onUnregisterClientSession)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .build();
    }

    private Behavior<ChatChannelReaderCommand> onSyncNewMessage(SyncNewMessage command) {
        messages.add(command.message());
        clientSession.tell(new DeliverNewMessage(command.message()));

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onSyncUpdate(SyncUpdate command) {
        ChatMessage updatedMessage = messages.update(
                command.messageId(),
                command.updatedMessage(),
                command.updatedAt()
        );

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

        return this;
    }

    private Behavior<ChatChannelReaderCommand> onUnregisterClientSession(UnregisterClientSession command) {
        clientSessions.remove(command.userId());

        return this;
    }

    private record HeartBeat() implements ChatChannelReaderCommand { }
    record DeliverSyncMessages(List<ChatMessage> messages) implements ChatChannelReaderCommand { }
}
