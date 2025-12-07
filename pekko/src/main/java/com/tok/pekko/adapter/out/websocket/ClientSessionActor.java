package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.ChannelReaderRegistryActor.GetChannelReaderActor;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.GetHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RequestInitialHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.UnregisterClientSession;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorMessagePort;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ReleaseClientSessionActor;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundRegisteredChannelIds;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateChangeChannelMembership;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateChangeChannelPolicy;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateChannelDeleted;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateEditChannelName;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateFailure;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateKickedMember;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateMembershipCount;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ReSyncSession;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.RequestHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncJoinChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncLeaveChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SessionPongReceived;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import java.time.Duration;
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

    private static final long WEBSOCKET_HEALTHCHECK_INTERVAL_SECONDS = 30L;
    private static final int MAX_MISSED_SESSION_PONGS = 2;

    public static Behavior<ClientSessionCommand> create(
            Long userId,
            ClientMessageSender clientMessageSender,
            MessageStoragePort messageStoragePort,
            ChannelMembershipActorMessagePort channelMembershipActorMessagePort,
            ActorRef<ChannelReaderRegistryCommand> readerRegistry
    ) {
        return Behaviors.setup(
                context -> {
                    channelMembershipActorMessagePort.sendParticipatingChannels(userId, context.getSelf());

                    return Behaviors.withTimers(
                            timers -> {
                                timers.startTimerAtFixedRate(
                                        new SessionHealthCheck(),
                                        Duration.ofSeconds(WEBSOCKET_HEALTHCHECK_INTERVAL_SECONDS)
                                );

                                return new ClientSessionActor(
                                        context,
                                        userId,
                                        clientMessageSender,
                                        messageStoragePort,
                                        channelMembershipActorMessagePort,
                                        readerRegistry
                                );
                            }
                    );
                }
        );
    }

    private final Long userId;
    private final ClientMessageSender clientMessageSender;
    private final MessageStoragePort messageStoragePort;
    private final ChannelMembershipActorMessagePort channelMembershipActorMessagePort;
    private final ActorRef<ChannelReaderRegistryCommand> readerRegistry;
    private final Map<Long, ActorRef<ChannelReaderCommand>> readers;
    private final Map<Long, RequestHistory> pendingRequestHistory;
    private int missedSessionPongs;

    private ClientSessionActor(
            ActorContext<ClientSessionCommand> context,
            Long userId,
            ClientMessageSender clientMessageSender,
            MessageStoragePort messageStoragePort,
            ChannelMembershipActorMessagePort channelMembershipActorMessagePort,
            ActorRef<ChannelReaderRegistryCommand> readerRegistry
    ) {
        super(context);

        this.userId = userId;
        this.messageStoragePort = messageStoragePort;
        this.channelMembershipActorMessagePort = channelMembershipActorMessagePort;
        this.clientMessageSender = clientMessageSender;
        this.readerRegistry = readerRegistry;
        this.readers = new HashMap<>();
        this.pendingRequestHistory = new HashMap<>();
        this.missedSessionPongs = 0;
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
                                  .onMessage(FoundChannelReaders.class, this::onFoundChannelReaders)
                                  .onMessage(SyncJoinChannel.class, this::onSyncJoinChannel)
                                  .onMessage(SyncLeaveChannel.class, this::onSyncLeaveChannel)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .onMessage(SessionPongReceived.class, this::onSessionPongReceived)
                                  .onMessage(SessionHealthCheck.class, this::onSessionHealthCheck)
                                  .onMessage(PropagateChangeChannelMembership.class, this::onPropagateChangeChannelMembership)
                                  .onMessage(PropagateMembershipCount.class, this::onPropagateMembershipCount)
                                  .onMessage(PropagateChangeChannelPolicy.class, this::onPropagateChangeChannelPolicy)
                                  .onMessage(PropagateEditChannelName.class, this::onPropagateEditChannelName)
                                  .onMessage(PropagateKickedMember.class, this::onPropagateKickedMember)
                                  .onMessage(PropagateChannelDeleted.class, this::onPropagateChannelDeleted)
                                  .onMessage(ReSyncSession.class, this::onReSyncSession)
                                  .onMessage(PropagateFailure.class, this::onPropagateFailure)
                                  .build();
    }

    private Behavior<ClientSessionCommand> onDeliverNewMessage(DeliverNewMessage command) {
        clientMessageSender.sendMessage(command.message());

        return this;
    }

    private Behavior<ClientSessionCommand> onDeliverUpdatedMessage(DeliverUpdatedMessage command) {
        clientMessageSender.sendUpdatedMessage(command.messageId(), command.updatedMessage(), command.updatedAt());

        return this;
    }

    private Behavior<ClientSessionCommand> onDeliverDeletedMessage(DeliverDeletedMessage command) {
        clientMessageSender.sendDeletedMessage(command.deletedMessageId());

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

        readerRegistry.tell(new ReleaseClientSessionActor(userId, channelIds));
        readers.values()
               .forEach(reader -> reader.tell(new UnregisterClientSession(userId)));
        readers.clear();
        pendingRequestHistory.clear();

        return Behaviors.stopped();
    }

    private Behavior<ClientSessionCommand> onSessionPongReceived(SessionPongReceived command) {
        missedSessionPongs = 0;

        return this;
    }

    private Behavior<ClientSessionCommand> onSessionHealthCheck(SessionHealthCheck command) {
        clientMessageSender.sendWebSocketPing();
        missedSessionPongs++;

        if (missedSessionPongs >= MAX_MISSED_SESSION_PONGS) {
            clientMessageSender.requestSessionReconnect();
            missedSessionPongs = 0;
        }

        return this;
    }

    private Behavior<ClientSessionCommand> onPropagateChangeChannelMembership(PropagateChangeChannelMembership command) {
        clientMessageSender.sendChangedChannelMembership(command.channelId(), command.channelMembership(), command.membershipCount());

        return this;
    }

    private Behavior<ClientSessionCommand> onPropagateMembershipCount(PropagateMembershipCount command) {
        clientMessageSender.sendChangedMembershipCount(command.channelId(), command.membershipCount());
        return this;
    }

    private Behavior<ClientSessionCommand> onPropagateChangeChannelPolicy(PropagateChangeChannelPolicy command) {
        clientMessageSender.sendChangedChannelPolicy(command.channelId(), command.channelPolicy());

        return this;
    }

    private Behavior<ClientSessionCommand> onPropagateEditChannelName(PropagateEditChannelName command) {
        clientMessageSender.sendEditedChannelName(command.channelId(), command.editedName());

        return this;
    }

    private Behavior<ClientSessionCommand> onPropagateKickedMember(PropagateKickedMember command) {
        clientMessageSender.sendKickedFromChannel(command.channelId());
        readers.remove(command.channelId());
        readerRegistry.tell(new ReleaseClientSessionActor(userId, List.of(command.channelId())));

        return this;
    }

    private Behavior<ClientSessionCommand> onPropagateChannelDeleted(PropagateChannelDeleted command) {
        clientMessageSender.sendChannelDeleted(command.channelId());
        readers.remove(command.channelId());
        readerRegistry.tell(new ReleaseClientSessionActor(userId, List.of(command.channelId())));

        return this;
    }

    private Behavior<ClientSessionCommand> onReSyncSession(ReSyncSession command) {
        channelMembershipActorMessagePort.sendParticipatingChannels(userId, getContext().getSelf());

        return this;
    }

    private Behavior<ClientSessionCommand> onPropagateFailure(PropagateFailure command) {
        clientMessageSender.sendError(command.channelId(), command.reason());

        return this;
    }

    // 요청한 채널 ID에 대해 ChannelReaderRegistryActor가 Singleton 방식으로 관리하는 ChannelReaderActor ActorRef를 전달받는 메시지 : ChannelReaderRegistryActor -> ClientSessionActor
    public record FoundChannelReaders(Map<Long, ActorRef<ChannelReaderCommand>> chatChannelReaderRefs) implements ClientSessionCommand { }

    // 30초 간격으로 트리거되는 타이머로 인해 전달되는 메시지 : ClientSessionActor -> ClientSessionActor
    private record SessionHealthCheck() implements ClientSessionCommand { }
}
