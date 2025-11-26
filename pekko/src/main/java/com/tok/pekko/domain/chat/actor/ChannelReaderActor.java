package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChannelEntity.RequestSyncMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveChannelMetadata;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveHistory;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveMembership;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.NotifyFailure;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.NotifyMembershipCountChanged;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RegisterClientSession;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.GetHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.RequestInitialHistory;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncChannelMetadata;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncMembership;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.UnregisterClientSession;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.SpawnedChannelReaderActor;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateChangeChannelMembership;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateChangeChannelPolicy;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateEditChannelName;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateFailure;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateKickedMember;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.PropagateMembershipCount;
import com.tok.pekko.domain.user.model.vo.UserId;
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
            ChannelReaderMessages messages,
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
    private final ChannelReaderMessages messages;
    private final EntityRef<ChannelEntityCommand> channelEntity;
    private final Map<Long, ActorRef<ClientSessionCommand>> clientSessions;
    private final List<RequestInitialHistory> requestInitialHistories;
    private final List<Runnable> pendingSyncEvents;
    private boolean initialHistorySynced;

    private ChannelReaderActor(
            ActorContext<ChannelReaderCommand> context,
            Long channelId,
            ChannelReaderMessages messages,
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
                                  .onMessage(NotifyChangeChannelPolicy.class, this::onNotifyChangeChannelPolicy)
                                  .onMessage(NotifyMemberLeft.class, this::onNotifyMemberLeft)
                                  .onMessage(NotifyKickedMember.class, this::onNotifyKickedMember)
                                  .onMessage(NotifyFailure.class, this::onNotifyFailure)
                                  .onMessage(NotifyEditChannelName.class, this::onNotifyEditChannelName)
                                  .onMessage(NotifyChangeChannelMembership.class, this::onNotifyChangeChannelMembership)
                                  .onMessage(NotifyMembershipCountChanged.class, this::onNotifyMembershipCountChanged)
                                  .onMessage(SyncMembership.class, this::onSyncMembership)
                                  .onMessage(SyncChannelMetadata.class, this::onSyncChannelMetadata)
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

        channelEntity.tell(new ResolveMembership(UserId.create(command.userId()), getContext().getSelf()));
        channelEntity.tell(new ResolveChannelMetadata(getContext().getSelf()));

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

    private Behavior<ChannelReaderCommand> onNotifyChangeChannelPolicy(NotifyChangeChannelPolicy command) {
        clientSessions.values()
                      .forEach(
                              clientSession ->
                                      clientSession.tell(
                                              new PropagateChangeChannelPolicy(channelId, command.channelPolicy())
                                      )
                      );

        return this;
    }

    private Behavior<ChannelReaderCommand> onNotifyMemberLeft(NotifyMemberLeft command) {
        ActorRef<ClientSessionCommand> clientSession = clientSessions.get(command.memberId());

        if (clientSession != null) {
            clientSession.tell(new ClientSessionProtocol.SyncLeaveChannel(channelId));
        }

        return this;
    }

    private Behavior<ChannelReaderCommand> onNotifyKickedMember(NotifyKickedMember command) {
        ActorRef<ClientSessionCommand> clientSession = clientSessions.remove(command.memberId());

        if (clientSession != null) {
            clientSession.tell(new PropagateKickedMember(channelId));
        }

        return this;
    }

    private Behavior<ChannelReaderCommand> onNotifyFailure(NotifyFailure command) {
        ActorRef<ClientSessionCommand> clientSession = clientSessions.get(command.userId());
        if (clientSession != null) {
            clientSession.tell(new PropagateFailure(channelId, command.reason()));
        }
        return this;
    }

    private Behavior<ChannelReaderCommand> onNotifyEditChannelName(NotifyEditChannelName command) {
        clientSessions.values()
                      .forEach(
                              clientSession -> clientSession.tell(
                                      new PropagateEditChannelName(
                                              channelId,
                                              command.editedName()
                                      )
                              )
                      );

        return this;
    }

    private Behavior<ChannelReaderCommand> onNotifyChangeChannelMembership(NotifyChangeChannelMembership command) {
        ActorRef<ClientSessionCommand> clientSession = clientSessions.get(command.memberId());

        if (clientSession != null) {
            clientSession.tell(
                    new PropagateChangeChannelMembership(
                            channelId,
                            command.channelMembership(),
                            command.membershipCount()
                    )
            );
        }

        return this;
    }

    private Behavior<ChannelReaderCommand> onNotifyMembershipCountChanged(NotifyMembershipCountChanged command) {
        clientSessions.values()
                      .forEach(
                              session -> session.tell(
                                      new PropagateMembershipCount(
                                              channelId,
                                              command.membershipCount()
                                      )
                              )
                      );
        return this;
    }

    private Behavior<ChannelReaderCommand> onSyncMembership(SyncMembership command) {
        ActorRef<ClientSessionCommand> clientSession = clientSessions.get(command.userId());

        if (clientSession != null) {
            clientSession.tell(
                    new PropagateChangeChannelMembership(
                            channelId,
                            command.membership(),
                            command.membershipCount()
                    )
            );
        }

        return this;
    }

    private Behavior<ChannelReaderCommand> onSyncChannelMetadata(SyncChannelMetadata command) {
        clientSessions.values()
                      .forEach(session -> {
                          session.tell(new PropagateEditChannelName(command.channelId(), command.name()));
                          session.tell(new PropagateChangeChannelPolicy(command.channelId(), command.channelPolicy()));
                          session.tell(new PropagateMembershipCount(command.channelId(), command.membershipCount()));
                      });
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

    // 30초 간격으로 ChannelEntity에 RequestSyncMessages를 보내도록 트리거하는 내부 타이머 메시지 : ChannelReaderActor -> ChannelReaderActor
    private record SyncMessageHeartBeat() implements ChannelReaderCommand { }

    // ChannelEntity가 동기화한 채팅 히스토리를 전달받는 메시지 : ChannelEntity -> ChannelReaderActor
    record DeliverSyncMessages(List<ChatMessage> messages) implements ChannelReaderCommand { }

    // ClientSessonActor에게 채널 정책 변경을 알리기 위한 메시지 : ChannelEntity -> ChannelReaderActor
    record NotifyChangeChannelPolicy(ChannelPolicy channelPolicy) implements ChannelReaderCommand { }

    // ClientSessionActor에게 채널 이름 변경을 알리기 위한 메시지 : ChannelEntity -> ChannelReaderActor
    record NotifyEditChannelName(String editedName) implements ChannelReaderCommand { }

    // ClientSessionActor에게 채널에서 강퇴되었음을 알리기 위한 메시지 : ChannelEntity -> ChannelReaderActor
    record NotifyKickedMember(Long memberId) implements ChannelReaderCommand { }

    // ClientSessionActor에게 채널 참여자의 정보가 변경되었음을 전파받기 위한 메시지 : ChannelEntity -> ChannelReaderActor
    record NotifyChangeChannelMembership(Long memberId, ChannelMembership channelMembership, int membershipCount) implements ChannelReaderCommand { }

    // ClientSessionActor에게 채널 탈퇴를 알리기 위한 메시지 : ChannelEntity -> ChannelReaderActor
    record NotifyMemberLeft(Long memberId) implements ChannelReaderCommand { }
}
