package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ChannelReaderRegistryActor.GetChannelReaderActorRef;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.PingHealthCheckFromClientSession;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.UnregisterClientSession;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundRegisteredChannelIds;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.JoinChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.LeaveChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PongHealthCheck;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncJoinChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncLeaveChannel;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.pekko.actor.Cancellable;
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
            ChannelMembershipPort channelMembershipPort,
            ActorRef<ChannelReaderRegistryCommand> readerRegistry
    ) {
        return Behaviors.setup(
                context -> {
                    channelMembershipPort.findParticipatingChannels(userId, context.getSelf());

                    return Behaviors.withTimers(
                            timers -> {
                                timers.startTimerAtFixedRate(new HealthCheckHeartBeat(), Duration.ofSeconds(30));

                                return new ClientSessionActor(
                                        context,
                                        userId,
                                        clientMessageSender,
                                        channelMembershipPort,
                                        readerRegistry
                                );
                            }
                    );
                }
        );
    }

    private final Long userId;
    private final ClientMessageSender clientMessageSender;
    private final ChannelMembershipPort channelMembershipPort;
    private final ActorRef<ChannelReaderRegistryCommand> readerRegistry;
    private final Map<Long, ActorRef<ChatChannelReaderCommand>> readers;
    private final Map<Long, Cancellable> healthCheckTimeouts;

    private ClientSessionActor(
            ActorContext<ClientSessionCommand> context,
            Long userId,
            ClientMessageSender clientMessageSender,
            ChannelMembershipPort channelMembershipPort,
            ActorRef<ChannelReaderRegistryCommand> readerRegistry
    ) {
        super(context);

        this.userId = userId;
        this.clientMessageSender = clientMessageSender;
        this.channelMembershipPort = channelMembershipPort;
        this.readerRegistry = readerRegistry;
        this.readers = new HashMap<>();
        this.healthCheckTimeouts = new HashMap<>();
    }

    @Override
    public Receive<ClientSessionCommand> createReceive() {
        return newReceiveBuilder().onMessage(DeliverNewMessage.class, this::onDeliverNewMessage)
                                  .onMessage(DeliverUpdatedMessage.class, this::onDeliverUpdatedMessage)
                                  .onMessage(DeliverDeletedMessage.class, this::onDeliverDeletedMessage)
                                  .onMessage(DeliverHistory.class, this::onDeliverHistory)
                                  .onMessage(FoundRegisteredChannelIds.class, this::onFoundRegisteredChannelIds)
                                  .onMessage(FoundChannelReaders.class, this::onFoundChannelReaders)
                                  .onMessage(JoinChannel.class, this::onJoinChannel)
                                  .onMessage(SyncJoinChannel.class, this::onSyncJoinChannel)
                                  .onMessage(LeaveChannel.class, this::onLeaveChannel)
                                  .onMessage(SyncLeaveChannel.class, this::onSyncLeaveChannel)
                                  .onMessage(HealthCheckHeartBeat.class, this::onHealthCheckHeartBeat)
                                  .onMessage(PongHealthCheck.class, this::onPongHealthCheck)
                                  .onMessage(PongHealthCheckTimeout.class, this::onPongHealthCheckTimeout)
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

    private Behavior<ClientSessionCommand> onDeliverHistory(DeliverHistory command) {
        clientMessageSender.sendMessages(command.messages());

        return this;
    }

    private Behavior<ClientSessionCommand> onFoundRegisteredChannelIds(FoundRegisteredChannelIds command) {
        readerRegistry.tell(new GetChannelReaderActorRef(command.channelIds(), userId, getContext().getSelf()));

        return this;
    }

    private Behavior<ClientSessionCommand> onFoundChannelReaders(FoundChannelReaders command) {
        readers.putAll(command.chatChannelReaderRefs());

        command.chatChannelReaderRefs()
               .values()
               .forEach(
                       reader -> {
                           getContext().watch(reader);
                           reader.tell(new RegisterClientSession(userId, getContext().getSelf()));
                       }
               );

        return this;
    }

    private Behavior<ClientSessionCommand> onJoinChannel(JoinChannel command) {
        channelMembershipPort.joinChannel(userId, command.channelId(), getContext().getSelf());
        return this;
    }

    private Behavior<ClientSessionCommand> onSyncJoinChannel(SyncJoinChannel command) {
        readerRegistry.tell(new GetChannelReaderActorRef(List.of(command.channelId()), userId, getContext().getSelf()));
        return this;
    }

    private Behavior<ClientSessionCommand> onLeaveChannel(LeaveChannel command) {
        channelMembershipPort.leaveChannel(userId, command.channelId(), getContext().getSelf());
        return this;
    }

    private Behavior<ClientSessionCommand> onSyncLeaveChannel(SyncLeaveChannel command) {
        ActorRef<ChatChannelReaderCommand> leaveReaderChannel = readers.remove(command.channelId());

        if (leaveReaderChannel != null) {
            leaveReaderChannel.tell(new UnregisterClientSession(userId));
        }

        return this;
    }

    private Behavior<ClientSessionCommand> onHealthCheckHeartBeat(HealthCheckHeartBeat command) {
        if (readers.isEmpty()) {
            return this;
        }

        for (Entry<Long, ActorRef<ChatChannelReaderCommand>> readerEntry : readers.entrySet()) {
            Long channelId = readerEntry.getKey();
            ActorRef<ChatChannelReaderCommand> readerRef = readerEntry.getValue();

            readerRef.tell(new PingHealthCheckFromClientSession(getContext().getSelf()));

            Cancellable timeoutSchedule = getContext().scheduleOnce(
                    Duration.ofSeconds(60L),
                    getContext().getSelf(),
                    new PongHealthCheckTimeout(channelId)
            );

            Cancellable oldSchedule = healthCheckTimeouts.put(channelId, timeoutSchedule);

            if (oldSchedule != null) {
                oldSchedule.cancel();
            }
        }

        return this;
    }

    private Behavior<ClientSessionCommand> onPongHealthCheckTimeout(PongHealthCheckTimeout command) {
        healthCheckTimeouts.remove(command.channelId());

        ActorRef<ChatChannelReaderCommand> readerRef = readers.remove(command.channelId());

        if (readerRef != null) {
            getContext().unwatch(readerRef);
        }

        readerRegistry.tell(new GetChannelReaderActorRef(List.of(command.channelId()), userId, getContext().getSelf()));

        return this;
    }

    private Behavior<ClientSessionCommand> onPongHealthCheck(PongHealthCheck command) {
        Cancellable schedule = healthCheckTimeouts.remove(command.channelId());

        if (schedule != null) {
            schedule.cancel();
        }

        return this;
    }

    private Behavior<ClientSessionCommand> onShutdown(Shutdown command) {
        return Behaviors.stopped();
    }

    public record FoundChannelReaders(Map<Long, ActorRef<ChatChannelReaderCommand>> chatChannelReaderRefs) implements ClientSessionCommand { }
    private record HealthCheckHeartBeat() implements ClientSessionCommand { }
    private record PongHealthCheckTimeout(Long channelId) implements ClientSessionCommand { }
}
