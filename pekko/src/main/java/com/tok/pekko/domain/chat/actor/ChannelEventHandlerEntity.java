package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.ChannelNameEdited;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.ChannelPolicyChanged;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.DemotedToMember;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.MemberKicked;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.MemberLeft;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.MessageDeleted;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.MessageEdited;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.PermissionAdded;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.PermissionRemoved;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.PromotedToManager;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.UserInvited;
import com.tok.pekko.domain.chat.actor.ChannelDomainEvent.UserJoined;
import com.tok.pekko.domain.chat.actor.ChannelEntity.DomainEventProcessed;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncStoredMembership;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.EventFailed;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.EventSucceeded;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.NotifyStoredMembership;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

public class ChannelEventHandlerEntity extends AbstractBehavior<ChannelEventHandlerCommand> {

    public static final EntityTypeKey<ChannelEventHandlerCommand> ENTITY_TYPE_KEY =
            EntityTypeKey.create(ChannelEventHandlerCommand.class, "ChannelEventHandler");

    public static Behavior<ChannelEventHandlerCommand> create(
            ClusterSharding clusterSharding,
            Long channelId,
            MessageStoragePort messageStoragePort,
            ChannelActorStoragePort channelActorStoragePort,
            ChannelMembershipActorStoragePort channelMembershipActorStoragePort
    ) {
        return Behaviors.setup(
                context -> {
                    EntityRef<ChannelEntityCommand> channelEntity = clusterSharding.entityRefFor(
                            ChannelEntity.ENTITY_TYPE_KEY,
                            String.valueOf(channelId)
                    );

                    return new ChannelEventHandlerEntity(
                            context,
                            channelEntity,
                            messageStoragePort,
                            channelActorStoragePort,
                            channelMembershipActorStoragePort
                    );
                }

        );
    }

    private final Map<Long, EventHolder> events;
    private final EntityRef<ChannelEntityCommand> channelEntity;
    private final MessageStoragePort messageStoragePort;
    private final ChannelActorStoragePort channelActorStoragePort;
    private final ChannelMembershipActorStoragePort channelMembershipActorStoragePort;

    private ChannelEventHandlerEntity(
            ActorContext<ChannelEventHandlerCommand> context,
            EntityRef<ChannelEntityCommand> channelEntity,
            MessageStoragePort messageStoragePort,
            ChannelActorStoragePort channelActorStoragePort,
            ChannelMembershipActorStoragePort channelMembershipActorStoragePort
    ) {
        super(context);
        this.events = new HashMap<>();
        this.channelEntity = channelEntity;
        this.messageStoragePort = messageStoragePort;
        this.channelActorStoragePort = channelActorStoragePort;
        this.channelMembershipActorStoragePort = channelMembershipActorStoragePort;
    }

    @Override
    public Receive<ChannelEventHandlerCommand> createReceive() {
        return newReceiveBuilder().onMessage(HandleChannelPolicyChanged.class, this::onHandleChannelPolicyChanged)
                                  .onMessage(HandleChannelNameEdited.class, this::onHandleChannelNameChanged)
                                  .onMessage(HandleUserJoined.class, this::onHandleUserJoined)
                                  .onMessage(HandleMemberLeft.class, this::onHandleMemberLeft)
                                  .onMessage(HandleUserInvited.class, this::onHandleUserInvited)
                                  .onMessage(HandlePromotedToManager.class, this::onHandlePromotedToManager)
                                  .onMessage(HandleDemotedToMember.class, this::onHandleDemotedToMember)
                                  .onMessage(HandlePermissionAdded.class, this::onHandlePermissionAdded)
                                  .onMessage(HandlePermissionRemoved.class, this::onHandlePermissionRemoved)
                                  .onMessage(HandleMemberKicked.class, this::onHandleMemberKicked)
                                  .onMessage(HandleMessageEdited.class, this::onHandleMessageEdited)
                                  .onMessage(EventSucceeded.class, this::onEventSucceeded)
                                  .onMessage(EventFailed.class, this::onEventFailed)
                                  .onMessage(NotifyStoredMembership.class, this::onNotifyMembershipStored)
                                  .onMessage(HandleMessageDeleted.class, this::onHandleMessageDeleted)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .build();
    }

    private Behavior<ChannelEventHandlerCommand> onHandleChannelPolicyChanged(HandleChannelPolicyChanged command) {
        ChannelPolicyChanged event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        channelActorStoragePort.update(event.channel(), event.eventId(), getContext().getSelf());
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandleChannelNameChanged(HandleChannelNameEdited command) {
        ChannelNameEdited event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        channelActorStoragePort.update(event.channel(), event.eventId(), getContext().getSelf());
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandleUserJoined(HandleUserJoined command) {
        UserJoined event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        channelMembershipActorStoragePort.join(
                event.eventId(),
                event.channelMembership().getChannelId(),
                event.channelMembership(),
                getContext().getSelf()
        );
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandleMemberLeft(HandleMemberLeft command) {
        MemberLeft event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        channelMembershipActorStoragePort.leave(event.eventId(), event.channelMembership(), getContext().getSelf());
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandleUserInvited(HandleUserInvited command) {
        UserInvited event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        channelMembershipActorStoragePort.inviteUser(
                event.eventId(),
                event.channelMembership().getChannelId(),
                event.channelMembership(),
                getContext().getSelf()
        );
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandlePromotedToManager(HandlePromotedToManager command) {
        PromotedToManager event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        channelMembershipActorStoragePort.promoteToManager(event.eventId(), event.channelMembership(), getContext().getSelf());
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandleDemotedToMember(HandleDemotedToMember command) {
        DemotedToMember event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        channelMembershipActorStoragePort.demoteToMember(event.eventId(), event.channelMembership(), getContext().getSelf());
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandlePermissionAdded(HandlePermissionAdded command) {
        PermissionAdded event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        channelMembershipActorStoragePort.addPermission(
                event.eventId(),
                event.channelMembership(),
                event.permissionType(),
                getContext().getSelf()
        );
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandlePermissionRemoved(HandlePermissionRemoved command) {
        PermissionRemoved event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        channelMembershipActorStoragePort.removePermission(
                event.eventId(),
                event.channelMembership(),
                event.permissionType(),
                getContext().getSelf()
        );
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandleMemberKicked(HandleMemberKicked command) {
        MemberKicked event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        channelMembershipActorStoragePort.kickMember(event.eventId(), event.channelMembership(), getContext().getSelf());
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandleMessageEdited(HandleMessageEdited command) {
        MessageEdited event = command.event;

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        messageStoragePort.update(event.eventId(), event.updatedMessage(), getContext().getSelf());
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onHandleMessageDeleted(HandleMessageDeleted command) {
        MessageDeleted event = command.event();

        if (events.containsKey(event.eventId())) {
            return this;
        }

        events.put(event.eventId(), new EventHolder(event));
        messageStoragePort.delete(event.eventId(), event.deletedMessageId(), getContext().getSelf());
        return this;
    }
    
    private Behavior<ChannelEventHandlerCommand> onEventSucceeded(EventSucceeded command) {
        EventHolder eventHolder = events.get(command.eventId());
        
        if (eventHolder == null) {
            return this;
        }

        eventHolder.eventStatus = EventStatus.SUCCEEDED;
        channelEntity.tell(new DomainEventProcessed(command.eventId()));
        return this;
    }
    
    private Behavior<ChannelEventHandlerCommand> onEventFailed(EventFailed command) {
        EventHolder eventHolder = events.get(command.eventId());

        if (eventHolder == null) {
            return this;
        }

        eventHolder.eventStatus = EventStatus.FAILED;
        eventHolder.throwable = command.throwable();
        channelEntity.tell(new DomainEventProcessed(command.eventId()));
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onNotifyMembershipStored(NotifyStoredMembership command) {
        channelEntity.tell(new SyncStoredMembership(command.channelMembership()));
        return this;
    }

    private Behavior<ChannelEventHandlerCommand> onShutdown(Shutdown command) {
        return Behaviors.stopped();
    }

    private static class EventHolder {

        private final ChannelDomainEvent event;
        private EventStatus eventStatus;
        private Throwable throwable = null;

        public EventHolder(ChannelDomainEvent event) {
            this.event = event;
            this.eventStatus = EventStatus.PENDING;
        }
    }

    private enum EventStatus {
        PENDING, SUCCEEDED, FAILED
    }

    // 채널 정책 변경 도메인 이벤트를 처리하는 메시지 : ChannelEntity -> ChannelEventHandlerEntity
    record HandleChannelPolicyChanged(ChannelPolicyChanged event) implements ChannelEventHandlerCommand { }

    // 채널 이름 변경 도메인 이벤트를 처리하는 메시지 : ChannelEntity -> ChannelEventHandlerEntity
    record HandleChannelNameEdited(ChannelNameEdited event) implements ChannelEventHandlerCommand { }

    // 채널 참여 도메인 이벤트를 처리하는 메시지 : ChannelEntity -> ChannelEventHandlerEntity
    record HandleUserJoined(UserJoined event) implements ChannelEventHandlerCommand { }

    // 채널 탈퇴 도메인 이벤트를 처리하는 메시지 : ChannelEntity -> ChannelEventHandlerEntity
    record HandleMemberLeft(MemberLeft event) implements ChannelEventHandlerCommand { }

    // 채널 초대 도메인 이벤트를 처리하는 메시지 : ChannelEntity -> ChannelEventHandlerEntity
    record HandleUserInvited(UserInvited event) implements ChannelEventHandlerCommand { }

    // 매니저 승격 도메인 이벤트를 처리하는 메시지 : ChannelEntity -> ChannelEventHandlerEntity
    record HandlePromotedToManager(PromotedToManager event) implements ChannelEventHandlerCommand { }

    // 멤버 강등 도메인 이벤트를 처리하는 메시지 : ChannelEntity -> ChannelEventHandlerEntity
    record HandleDemotedToMember(DemotedToMember event) implements ChannelEventHandlerCommand { }

    // 권한 추가 도메인 이벤트를 처리하는 메시지 : ChannelEntity -> ChannelEventHandlerEntity
    record HandlePermissionAdded(PermissionAdded event) implements ChannelEventHandlerCommand { }

    // 권한 삭제 도메인 이벤트를 처리하는 메시지 : ChannelEntity -> ChannelEventHandlerEntity
    record HandlePermissionRemoved(PermissionRemoved event) implements ChannelEventHandlerCommand { }

    // 멤버 강퇴 도메인 이벤트를 처리하는 메시지 : ChannelEntity -> ChannelEventHandlerEntity
    record HandleMemberKicked(MemberKicked event) implements ChannelEventHandlerCommand { }

    // 채팅 메시지 변경 도메인 이벤트 : ChannelEntity -> ChannelEventHandlerEntity
    record HandleMessageEdited(MessageEdited event) implements ChannelEventHandlerCommand { }

    // 채팅 메시지 삭제 도메인 이벤트 : ChannelEntity -> ChannelEventHandlerEntity
    record HandleMessageDeleted(MessageDeleted event) implements ChannelEventHandlerCommand { }
}
