package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
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
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleChannelNameEdited;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleChannelPolicyChanged;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleDemotedToMember;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleMemberKicked;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleMemberLeft;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleMessageDeleted;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleMessageEdited;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandlePermissionAdded;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandlePermissionRemoved;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandlePromotedToManager;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleUserInvited;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleUserJoined;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.DeliverSyncMessages;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyChangeChannelMembership;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyChangeChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyEditChannelName;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyKickedMember;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyMemberLeft;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.AddPermission;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChangeChannelPolicy;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.DeleteMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.DemoteToMember;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.EditChannelName;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.InviteUser;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.JoinUser;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.JoinedUser;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.KickMember;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.LeaveMember;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.PromoteToManager;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RemovePermission;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveChannelMetadata;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveHistory;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveMembership;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SendMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncChannel;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncRecentMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncStoredMembership;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.UpdateMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.NotifyFailure;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.NotifyMembershipCountChanged;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncChannelMetadata;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncMembership;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.InviteUserEventProtocol.InviteUserEventCommand;
import com.tok.pekko.domain.chat.port.out.InviteUserEventProtocol.Invited;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.pubsub.Topic;
import org.apache.pekko.actor.typed.pubsub.Topic.Command;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

public class ChannelEntity extends AbstractBehavior<ChannelEntityCommand> {

    public static final EntityTypeKey<ChannelEntityCommand> ENTITY_TYPE_KEY =
            EntityTypeKey.create(ChannelEntityCommand.class, "ChatChannel");
    private static final int DEFAULT_RECENT_MESSAGE_SIZE = 50;

    public static Behavior<ChannelEntityCommand> create(
            Clock clock,
            Long channelId,
            ChatMessages messages,
            ClusterSharding clusterSharding,
            MessageStoragePort messageStoragePort,
            ChannelActorStoragePort channelActorStoragePort,
            ActorRef<Topic.Command<InviteUserEventCommand>> inviteUserTopic
    ) {
        return Behaviors.setup(
                context -> {
                    messageStoragePort.findRecentMessages(channelId, DEFAULT_RECENT_MESSAGE_SIZE, context.getSelf());
                    channelActorStoragePort.find(channelId, context.getSelf());

                    EntityRef<ChannelEventHandlerCommand> channelEventHandler = clusterSharding.entityRefFor(
                            ChannelEventHandlerEntity.ENTITY_TYPE_KEY,
                            String.valueOf(channelId)
                    );

                    return Behaviors.withTimers(
                            timer -> {
                                timer.startTimerAtFixedRate(new DomainEventHeartBeat(), Duration.ofSeconds(120L));

                                return new ChannelEntity(
                                        context,
                                        clock,
                                        channelId,
                                        messages,
                                        messageStoragePort,
                                        channelEventHandler,
                                        inviteUserTopic
                                );
                            }
                    );
                }
        );
    }

    private final Clock clock;
    private final Long channelId;
    private final ChatMessages messages;
    private final ChannelHolder channelHolder;
    private final Map<Long, EventHolder> events;
    private final MessageStoragePort messageStoragePort;
    private final SnowflakeSequenceGenerator eventIdGenerator;
    private final SnowflakeSequenceGenerator sequenceGenerator;
    private final List<ChannelEntityCommand> pendingChannelCommands;
    private final Map<String, ActorRef<ChannelReaderCommand>> readers;
    private final EntityRef<ChannelEventHandlerCommand> channelEventHandler;
    private final ActorRef<Command<InviteUserEventCommand>> inviteUserTopic;
    private final Map<String, ActorRef<ChannelReaderCommand>> pendingInitialSyncReaders;
    private boolean initialMessagesLoaded;

    private ChannelEntity(
            ActorContext<ChannelEntityCommand> context,
            Clock clock,
            Long channelId,
            ChatMessages messages,
            MessageStoragePort messageStoragePort,
            EntityRef<ChannelEventHandlerCommand> channelEventHandler,
            ActorRef<Command<InviteUserEventCommand>> inviteUserTopic
    ) {
        super(context);

        this.clock = clock;
        this.channelId = channelId;
        this.messages = messages;
        this.messageStoragePort = messageStoragePort;
        this.inviteUserTopic = inviteUserTopic;
        this.channelEventHandler = channelEventHandler;
        this.events = new HashMap<>();
        this.readers = new HashMap<>();
        this.channelHolder = new ChannelHolder();
        this.pendingChannelCommands = new ArrayList<>();
        this.pendingInitialSyncReaders = new HashMap<>();
        this.eventIdGenerator = new SnowflakeSequenceGenerator(channelId);
        this.sequenceGenerator = new SnowflakeSequenceGenerator(channelId);
        this.initialMessagesLoaded = false;
    }

    @Override
    public Receive<ChannelEntityCommand> createReceive() {
        return newReceiveBuilder().onMessage(SyncRecentMessages.class, this::onSyncRecentMessages)
                                  .onMessage(RegisterReader.class, this::onRegisterReader)
                                  .onMessage(SendMessage.class, this::onSendMessage)
                                  .onMessage(UpdateMessage.class, this::onUpdateMessage)
                                  .onMessage(DeleteMessage.class, this::onDeleteMessage)
                                  .onMessage(SyncPersistedMessage.class, this::onSyncPersistedMessage)
                                  .onMessage(RemoveShutdownReader.class, this::onRemoveShutdownReader)
                                  .onMessage(RequestSyncMessages.class, this::onRequestSyncMessages)
                                  .onMessage(ResolveHistory.class, this::onResolveHistory)
                                  .onMessage(ChangeChannelPolicy.class, this::onChangeChannelPolicy)
                                  .onMessage(EditChannelName.class, this::onEditChannelName)
                                  .onMessage(JoinUser.class, this::onJoinUser)
                                  .onMessage(LeaveMember.class, this::onLeaveMember)
                                  .onMessage(InviteUser.class, this::onInviteUser)
                                  .onMessage(PromoteToManager.class, this::onPromoteToManager)
                                  .onMessage(DemoteToMember.class, this::onDemoteToMember)
                                  .onMessage(AddPermission.class, this::onAddPermission)
                                  .onMessage(RemovePermission.class, this::onRemovePermission)
                                  .onMessage(KickMember.class, this::onKickMember)
                                  .onMessage(ResolveMembership.class, this::onResolveMembership)
                                  .onMessage(DomainEventHeartBeat.class, this::onDomainEventHeartBeat)
                                  .onMessage(DomainEventProcessed.class, this::onDomainEventProcessed)
                                  .onMessage(ResolveChannelMetadata.class, this::onResolveChannelMetadata)
                                  .onMessage(SyncChannel.class, this::onSyncChannel)
                                  .onMessage(SyncStoredMembership.class, this::onSyncStoredMembership)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .build();
    }

    private Behavior<ChannelEntityCommand> onSyncRecentMessages(SyncRecentMessages command) {
        this.messages.syncMessages(command.messages());

        if (!initialMessagesLoaded) {
            broadcastSyncMessages(command.messages());
            initialMessagesLoaded = true;
            pendingInitialSyncReaders.clear();
        }

        return this;
    }

    private Behavior<ChannelEntityCommand> onRegisterReader(RegisterReader command) {
        readers.put(command.readerName(), command.reader());

        return this;
    }

    private Behavior<ChannelEntityCommand> onSendMessage(SendMessage command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        try {
            this.channelHolder.channel.validateMemberSendMessage(UserId.create(command.userId()));
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.userId(), ex.getMessage());
            return this;
        }

        ChatMessage message = createChatMessage(command);

        messageStoragePort.store(message, getContext().getSelf());
        return this;
    }

    private Behavior<ChannelEntityCommand> onUpdateMessage(UpdateMessage command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            ChatMessage updatedMessage = messages.update(
                    this.channelHolder.channel,
                    command.executorId(),
                    command.messageId(),
                    command.updatedMessage(),
                    now
            );

            Long eventId = eventIdGenerator.nextSequence();
            MessageEdited messageEditedEvent = new MessageEdited(
                    channelId,
                    eventId,
                    now,
                    updatedMessage
            );
            EventHolder eventHolder = new EventHolder(
                    messageEditedEvent,
                    event -> channelEventHandler.tell(new HandleMessageEdited(event))
            );

            events.put(eventId, eventHolder);
            channelEventHandler.tell(new HandleMessageEdited(messageEditedEvent));
            readers.values()
                   .forEach(
                           reader -> reader.tell(
                                   new SyncUpdate(command.messageId(), command.updatedMessage(), now)
                           )
                   );
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.executorId(), ex.getMessage());
        }

        return this;
    }

    private Behavior<ChannelEntityCommand> onDeleteMessage(DeleteMessage command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }
        try {
            ChatMessage deletedMessage = messages.delete(
                    this.channelHolder.channel,
                    command.executorId(),
                    command.messageId()
            );

            Long eventId = eventIdGenerator.nextSequence();
            MessageDeleted messageDeletedEvent = new MessageDeleted(
                    channelId, eventId, LocalDateTime.now(clock),
                    deletedMessage.messageId()
            );
            EventHolder eventHolder = new EventHolder(
                    messageDeletedEvent,
                    event -> channelEventHandler.tell(new HandleMessageDeleted(event))
            );

            events.put(eventId, eventHolder);
            channelEventHandler.tell(new HandleMessageDeleted(messageDeletedEvent));
            readers.values()
                   .forEach(reader -> reader.tell(new SyncDeletion(command.messageId())));
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.executorId().getValue(), ex.getMessage());
        }

        return this;
    }

    private Behavior<ChannelEntityCommand> onSyncPersistedMessage(SyncPersistedMessage command) {
        messages.add(command.message());
        readers.values()
               .forEach(reader -> reader.tell(new SyncNewMessage(command.message())));

        return this;
    }

    private Behavior<ChannelEntityCommand> onRemoveShutdownReader(RemoveShutdownReader command) {
        readers.remove(command.readerName());

        return this;
    }

    private Behavior<ChannelEntityCommand> onRequestSyncMessages(RequestSyncMessages command) {
        if (!initialMessagesLoaded) {
            pendingInitialSyncReaders.put(command.secondary().path().name(), command.secondary());
            return this;
        }

        command.secondary()
               .tell(new DeliverSyncMessages(messages.getMessages()));

        return this;
    }

    private Behavior<ChannelEntityCommand> onResolveHistory(ResolveHistory command) {
        List<ChatMessage> history = this.messages.getHistory(command.messageSequence(), command.size());

        command.replyTo()
               .tell(new DeliverHistory(channelId, command.messageSequence(), command.size(), history));
        return this;
    }

    private Behavior<ChannelEntityCommand> onChangeChannelPolicy(ChangeChannelPolicy command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        try {
            ChannelPolicy changedPolicy = new ChannelPolicy(
                    command.canEditOwnMessage(),
                    command.canDeleteOwnMessage(),
                    command.isPublic()
            );

            this.channelHolder.channel = this.channelHolder.channel.changeChannelPolicy(
                    command.changerId(),
                    changedPolicy
            );
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.changerId().getValue(), ex.getMessage());
        }

        Long eventId = eventIdGenerator.nextSequence();
        ChannelPolicyChanged channelPolicyChangedEvent = new ChannelPolicyChanged(
                channelId,
                eventId,
                LocalDateTime.now(clock),
                this.channelHolder.channel
        );
        EventHolder eventHolder = new EventHolder(
                channelPolicyChangedEvent,
                event -> channelEventHandler.tell(new HandleChannelPolicyChanged(event))
        );

        events.put(eventId, eventHolder);
        channelEventHandler.tell(new HandleChannelPolicyChanged(channelPolicyChangedEvent));
        readers.values()
               .forEach(reader ->
                       reader.tell(
                               new NotifyChangeChannelPolicy(this.channelHolder.channel.getChannelPolicy()
                               )
                       )
               );
        return this;
    }

    private Behavior<ChannelEntityCommand> onEditChannelName(EditChannelName command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        try {
            this.channelHolder.channel = this.channelHolder.channel.editName(
                    command.changerId(),
                    command.changedName()
            );
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.changerId().getValue(), ex.getMessage());
        }

        Long eventId = eventIdGenerator.nextSequence();
        ChannelNameEdited channelNameEditedEvent = new ChannelNameEdited(
                channelId,
                eventId,
                LocalDateTime.now(clock),
                this.channelHolder.channel
        );
        EventHolder eventHolder = new EventHolder(
                channelNameEditedEvent,
                event -> channelEventHandler.tell(new HandleChannelNameEdited(event))
        );

        events.put(eventId, eventHolder);
        channelEventHandler.tell(new HandleChannelNameEdited(channelNameEditedEvent));
        readers.values()
               .forEach(reader -> reader.tell(new NotifyEditChannelName(this.channelHolder.channel.getName())));
        return this;
    }

    private Behavior<ChannelEntityCommand> onJoinUser(JoinUser command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        LocalDateTime now = LocalDateTime.now(clock);

        try {
            ChannelMembership joinerMembership = this.channelHolder.channel.joinUser(
                    command.joinerId(),
                    ChannelRole.MEMBER,
                    now
            );
            Long eventId = eventIdGenerator.nextSequence();
            UserJoined userJoinedEvent = new UserJoined(channelId, eventId, now, joinerMembership);
            EventHolder eventHolder = new EventHolder(
                    userJoinedEvent,
                    event -> channelEventHandler.tell(new HandleUserJoined(userJoinedEvent))
            );

            events.put(eventId, eventHolder);
            channelEventHandler.tell(new HandleUserJoined(userJoinedEvent));
            command.replyTo()
                   .tell(new JoinedUser(channelId, command.joinerId().getValue()));
            broadcastMembershipCount();
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.joinerId().getValue(), ex.getMessage());
        }

        return this;
    }

    private Behavior<ChannelEntityCommand> onLeaveMember(LeaveMember command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        try {
            ChannelMembership leaveMembership = this.channelHolder.channel.leaveMember(command.memberId());

            Long eventId = eventIdGenerator.nextSequence();
            MemberLeft memberLeftEvent = new MemberLeft(channelId, eventId, LocalDateTime.now(clock), leaveMembership);
            EventHolder eventHolder = new EventHolder(
                    memberLeftEvent,
                    event -> channelEventHandler.tell(new HandleMemberLeft(memberLeftEvent))
            );

            events.put(eventId, eventHolder);
            channelEventHandler.tell(new HandleMemberLeft(memberLeftEvent));
            broadcastMembershipCount();
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.memberId().getValue(), ex.getMessage());
        }

        readers.values()
               .forEach(reader -> reader.tell(new NotifyMemberLeft(command.memberId().getValue())));
        broadcastMembershipCount();
        return this;
    }

    private Behavior<ChannelEntityCommand> onInviteUser(InviteUser command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        LocalDateTime now = LocalDateTime.now(clock);

        try {
            ChannelMembership inviteeMembership = this.channelHolder.channel.inviteMember(
                    command.inviterId(),
                    command.inviteeId(),
                    now
            );

            Long eventId = eventIdGenerator.nextSequence();
            UserInvited userInvitedEvent = new UserInvited(channelId, eventId, now, inviteeMembership);
            EventHolder eventHolder = new EventHolder(
                    userInvitedEvent,
                    event -> channelEventHandler.tell(new HandleUserInvited(userInvitedEvent))
            );

            events.put(eventId, eventHolder);
            channelEventHandler.tell(new HandleUserInvited(userInvitedEvent));
            inviteUserTopic.tell(Topic.publish(new Invited(channelId, command.inviteeId().getValue())));
            broadcastMembershipCount();
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.inviterId().getValue(), ex.getMessage());
        }

        return this;
    }

    private Behavior<ChannelEntityCommand> onPromoteToManager(PromoteToManager command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        try {
            ChannelMembership managerMembership = this.channelHolder.channel.promoteToManager(
                    command.executorId(),
                    command.targetUserId()
            );
            Long eventId = eventIdGenerator.nextSequence();
            PromotedToManager promotedToManagerEvent = new PromotedToManager(
                    channelId,
                    eventId,
                    LocalDateTime.now(clock),
                    managerMembership
            );
            EventHolder eventHolder = new EventHolder(
                    promotedToManagerEvent,
                    event -> channelEventHandler.tell(new HandlePromotedToManager(promotedToManagerEvent))
            );

            events.put(eventId, eventHolder);
            channelEventHandler.tell(new HandlePromotedToManager(promotedToManagerEvent));
            readers.values()
                   .forEach(
                           reader ->
                                   reader.tell(
                                           new NotifyChangeChannelMembership(
                                                   command.targetUserId().getValue(),
                                                   managerMembership,
                                                   currentMembershipCount()
                                           )
                                   )
                   );
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.executorId().getValue(), ex.getMessage());
        }

        return this;
    }

    private Behavior<ChannelEntityCommand> onDemoteToMember(DemoteToMember command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        try {
            ChannelMembership memberMembership = this.channelHolder.channel.demoteToMember(
                    command.executorId(),
                    command.targetUserId()
            );
            Long eventId = eventIdGenerator.nextSequence();
            DemotedToMember demotedToMemberEvent = new DemotedToMember(
                    channelId,
                    eventId,
                    LocalDateTime.now(clock),
                    memberMembership
            );
            EventHolder eventHolder = new EventHolder(
                    demotedToMemberEvent,
                    event -> channelEventHandler.tell(new HandleDemotedToMember(demotedToMemberEvent))
            );

            events.put(eventId, eventHolder);
            channelEventHandler.tell(new HandleDemotedToMember(demotedToMemberEvent));
            readers.values()
                   .forEach(
                           reader ->
                                   reader.tell(
                                           new NotifyChangeChannelMembership(
                                                   command.targetUserId().getValue(),
                                                   memberMembership,
                                                   currentMembershipCount()
                                           )
                                   )
                   );
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.executorId().getValue(), ex.getMessage());
        }

        return this;
    }

    private Behavior<ChannelEntityCommand> onAddPermission(AddPermission command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        try {
            ChannelMembership addedPermissionMembership = this.channelHolder.channel.addPermission(
                    command.grantorId(),
                    command.granteeId(),
                    command.permission()
            );
            Long eventId = eventIdGenerator.nextSequence();
            PermissionAdded permissionAddedEvent = new PermissionAdded(
                    channelId,
                    eventId,
                    LocalDateTime.now(clock),
                    addedPermissionMembership,
                    command.permission()
            );
            EventHolder eventHolder = new EventHolder(
                    permissionAddedEvent,
                    event -> channelEventHandler.tell(new HandlePermissionAdded(permissionAddedEvent))
            );

            events.put(eventId, eventHolder);
            channelEventHandler.tell(new HandlePermissionAdded(permissionAddedEvent));
            readers.values()
                   .forEach(
                           reader ->
                                   reader.tell(
                                           new NotifyChangeChannelMembership(
                                                   command.granteeId().getValue(),
                                                   addedPermissionMembership,
                                                   currentMembershipCount()
                                           )
                                   )
                   );
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.grantorId().getValue(), ex.getMessage());
        }

        return this;
    }

    private Behavior<ChannelEntityCommand> onRemovePermission(RemovePermission command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        try {
            ChannelMembership removedPermissionMembership = this.channelHolder.channel.removePermission(
                    command.grantorId(),
                    command.granteeId(),
                    command.permission()
            );
            Long eventId = eventIdGenerator.nextSequence();
            PermissionRemoved permissionRemovedEvent = new PermissionRemoved(
                    channelId,
                    eventId,
                    LocalDateTime.now(clock),
                    removedPermissionMembership,
                    command.permission()
            );
            EventHolder eventHolder = new EventHolder(
                    permissionRemovedEvent,
                    event -> channelEventHandler.tell(new HandlePermissionRemoved(event))
            );

            events.put(eventId, eventHolder);
            channelEventHandler.tell(new HandlePermissionRemoved(permissionRemovedEvent));
            readers.values()
                   .forEach(
                           reader ->
                                   reader.tell(
                                           new NotifyChangeChannelMembership(
                                                   command.granteeId().getValue(),
                                                   removedPermissionMembership,
                                                   currentMembershipCount()
                                           )
                                   )
                   );
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.grantorId().getValue(), ex.getMessage());
        }

        return this;
    }

    private Behavior<ChannelEntityCommand> onKickMember(KickMember command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        try {
            ChannelMembership kickedMembership = this.channelHolder.channel.kickMember(
                    command.executorId(),
                    command.targetUserId()
            );
            Long eventId = eventIdGenerator.nextSequence();
            MemberKicked memberKickedEvent = new MemberKicked(
                    channelId,
                    eventId,
                    LocalDateTime.now(clock),
                    kickedMembership
            );
            EventHolder eventHolder = new EventHolder(
                    memberKickedEvent,
                    event -> channelEventHandler.tell(new HandleMemberKicked(memberKickedEvent))
            );

            events.put(eventId, eventHolder);
            channelEventHandler.tell(new HandleMemberKicked(memberKickedEvent));
            readers.values()
                   .forEach(reader -> reader.tell(new NotifyKickedMember(command.targetUserId().getValue())));
            broadcastMembershipCount();
        } catch (IllegalArgumentException ex) {
            notifyFailure(command.executorId().getValue(), ex.getMessage());
        }

        return this;
    }

    private Behavior<ChannelEntityCommand> onSyncStoredMembership(SyncStoredMembership command) {
        if (this.channelHolder.channel == null) {
            deferUntilChannelSynced(command);
            return this;
        }

        this.channelHolder.channel.syncMembership(command.channelMembership());
        return this;
    }

    private Behavior<ChannelEntityCommand> onResolveMembership(ResolveMembership command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        ChannelMembership membership = this.channelHolder.channel.getMemberships()
                                                                 .get(command.userId());
        if (membership != null) {
            command.replyTo()
                   .tell(
                           new SyncMembership(
                                   command.userId().getValue(),
                                   membership,
                                   currentMembershipCount()
                           )
                   );
        }
        return this;
    }

    private Behavior<ChannelEntityCommand> onDomainEventHeartBeat(DomainEventHeartBeat command) {
        events.values()
              .forEach(EventHolder::execute);

        return this;
    }

    private Behavior<ChannelEntityCommand> onDomainEventProcessed(DomainEventProcessed command) {
        events.remove(command.eventId());

        return this;
    }

    private Behavior<ChannelEntityCommand> onResolveChannelMetadata(ResolveChannelMetadata command) {
        if (deferUntilChannelSynced(command)) {
            return this;
        }

        command.replyTo()
               .tell(
                       new SyncChannelMetadata(
                               this.channelHolder.channel.getChannelId().getValue(),
                               this.channelHolder.channel.getName(),
                               this.channelHolder.channel.getChannelPolicy(),
                               this.channelHolder.channel.getMemberships().size()
                       )
               );
        return this;
    }

    private Behavior<ChannelEntityCommand> onSyncChannel(SyncChannel command) {
        this.channelHolder.channel = command.channel();
        processPendingChannelCommands();

        return this;
    }

    private Behavior<ChannelEntityCommand> onShutdown(Shutdown command) {
        return Behaviors.stopped();
    }

    private ChatMessage createChatMessage(SendMessage command) {
        long messageSequence = sequenceGenerator.nextSequence();
        LocalDateTime now = LocalDateTime.now(clock);

        return ChatMessage.create(
                channelId,
                command.userId(),
                messageSequence,
                command.message(),
                now,
                now
        );
    }

    private void broadcastMembershipCount() {
        int membershipCount = currentMembershipCount();

        readers.values()
               .forEach(reader -> reader.tell(new NotifyMembershipCountChanged(membershipCount)));
    }

    private int currentMembershipCount() {
        if (this.channelHolder.channel == null) {
            return 0;
        }

        return this.channelHolder.channel.getMemberships().size();
    }

    private boolean deferUntilChannelSynced(ChannelEntityCommand command) {
        if (this.channelHolder.channel != null) {
            return false;
        }

        pendingChannelCommands.add(command);
        return true;
    }

    private void processPendingChannelCommands() {
        if (pendingChannelCommands.isEmpty()) {
            return;
        }

        List<ChannelEntityCommand> pending = new ArrayList<>(pendingChannelCommands);

        pendingChannelCommands.clear();
        pending.forEach(command -> getContext().getSelf().tell(command));
    }

    private void notifyFailure(Long userId, String reason) {
        readers.values()
               .forEach(
                       reader -> reader.tell(
                               new NotifyFailure(userId, reason)
                       )
               );
    }

    private void broadcastSyncMessages(List<ChatMessage> syncedMessages) {
        pendingInitialSyncReaders.values()
                                 .forEach(reader -> reader.tell(new DeliverSyncMessages(syncedMessages)));
    }

    private static class ChannelHolder {

        private Channel channel;

        private ChannelHolder() {
            this.channel = null;
        }
    }

    private static class EventHolder {

        private final ChannelDomainEvent event;
        private final Consumer<ChannelDomainEvent> handler;

        @SuppressWarnings("unchecked")
        private <T extends ChannelDomainEvent> EventHolder(T event, Consumer<T> handler) {
            this.event = event;
            this.handler = (Consumer<ChannelDomainEvent>) handler;
        }

        private void execute() {
            handler.accept(event);
        }
    }

    // 채팅 히스토리 동기화를 요청하는 메시지 : ChannelReaderActor -> ChannelEntity
    record RequestSyncMessages(ActorRef<ChannelReaderCommand> secondary) implements ChannelEntityCommand { }

    // 이벤트 처리가 완료되었음을 전파받는 메시지 : ChannelEventHandlerEntity -> ChannelEntity
    record DomainEventProcessed(Long eventId) implements ChannelEntityCommand { }

    // 아직 처리되지 않은 이벤트에 대한 처리를 진행하기 위한 메시지 : ChannelEntity -> ChannelEntity
    private record DomainEventHeartBeat() implements ChannelEntityCommand { }
}
