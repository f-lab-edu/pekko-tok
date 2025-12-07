package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent.ChannelNameEdited;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent.ChannelPolicyChanged;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent.DemotedToMember;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent.MemberKicked;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent.MemberLeft;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent.PermissionAdded;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent.PermissionRemoved;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent.PromotedToManager;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent.UserInvited;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent.UserJoined;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.DeliverSyncMessages;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyChangeChannelMembership;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyChangeChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyEditChannelName;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyKickedMember;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.NotifyMemberLeft;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyChannelNameEdited;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyChannelPolicyChanged;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyChannelDeleted;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyDemotedToMember;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyMemberKicked;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyMemberLeft;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyPermissionAdded;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyPermissionRemoved;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyPromotedToManager;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyUserInvited;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyUserJoined;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelBatchPersisted;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelLoadFailed;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelLoaded;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelMembershipBatchPersisted;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelNotFound;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.DeleteMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ResolveHistory;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SendMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncDeletedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncRecentMessages;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncUpdatedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.UpdateMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.NotifyChannelDeleted;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.NotifyMembershipCountChanged;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncChannelMetadata;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncMembership;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Builder;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

public class ChannelEntity extends AbstractBehavior<ChannelEntityCommand> {

    public static final EntityTypeKey<ChannelEntityCommand> ENTITY_TYPE_KEY =
            EntityTypeKey.create(ChannelEntityCommand.class, "ChatChannel");

    private static final int DEFAULT_RECENT_MESSAGE_SIZE = 50;

    public static Behavior<ChannelEntityCommand> create(
            Clock clock,
            Long channelId,
            ChannelEntityChatMessages messages,
            MessageStoragePort messageStoragePort,
            ChannelActorStoragePort channelActorStoragePort,
            ChannelMembershipActorStoragePort channelMembershipActorStoragePort
    ) {
        return Behaviors.setup(
                context -> {
                    messageStoragePort.findRecentMessages(channelId, DEFAULT_RECENT_MESSAGE_SIZE, context.getSelf());
                    channelActorStoragePort.find(channelId, context.getSelf());

                    ChannelStateHolder channelStateHolder = ChannelStateHolder.uninitialized();

                    return ChannelEntity.builder()
                                        .context(context)
                                        .clock(clock)
                                        .channelId(channelId)
                                        .messages(messages)
                                        .channelState(channelStateHolder)
                                        .messageStoragePort(messageStoragePort)
                                        .channelActorStoragePort(channelActorStoragePort)
                                        .channelMembershipActorStoragePort(channelMembershipActorStoragePort)
                                        .build();
                }
        );
    }

    // 공통 의존성 / 식별자
    private final Clock clock;
    private final Long channelId;

    // 채팅 메시지
    private final ChannelEntityChatMessages messages;
    private final MessageStoragePort messageStoragePort;
    private final SnowflakeSequenceGenerator messageSequenceGenerator;

    // 채널 도메인 상태
    private final ChannelStateHolder channelState;

    // 채널 / 멤버십 DB 영속화 Port
    private final ChannelActorStoragePort channelActorStoragePort;
    private final ChannelMembershipActorStoragePort channelMembershipActorStoragePort;

    // Reader 관리
    private final Map<String, ActorRef<ChannelReaderCommand>> readers;

    // 배치 관련
    private boolean channelBatchRunning;
    private final SnowflakeSequenceGenerator batchSequenceGenerator;

    // 커맨드 버퍼링
    private final Deque<ChannelDomainEvent> pendingChannelEvents;
    private final Deque<ChannelEntityCommand> bufferedCommandsBeforeInit;
    private final Deque<Consumer<ChannelEntity>> pendingNotifications;
    private final Map<Long, ChannelBatchState> channelBatchStates;

    @Builder(access = AccessLevel.PRIVATE)
    private ChannelEntity(
            ActorContext<ChannelEntityCommand> context,
            Clock clock,
            Long channelId,
            ChannelStateHolder channelState,
            ChannelEntityChatMessages messages,
            MessageStoragePort messageStoragePort,
            ChannelActorStoragePort channelActorStoragePort,
            ChannelMembershipActorStoragePort channelMembershipActorStoragePort
    ) {
        super(context);

        this.clock = clock;
        this.channelId = channelId;

        // 메시지 관련
        this.messages = messages;
        this.messageStoragePort = messageStoragePort;
        this.messageSequenceGenerator = new SnowflakeSequenceGenerator(channelId);
        this.batchSequenceGenerator = new SnowflakeSequenceGenerator(channelId);

        // 채널 상태 & 배치 관련
        this.channelState = channelState;
        this.pendingChannelEvents = new ArrayDeque<>();
        this.channelBatchRunning = false;
        this.pendingNotifications = new ArrayDeque<>();
        this.channelBatchStates = new HashMap<>();

        // 외부 포트
        this.channelActorStoragePort = channelActorStoragePort;
        this.channelMembershipActorStoragePort = channelMembershipActorStoragePort;

        // 리더 & 명령 버퍼
        this.readers = new HashMap<>();
        this.bufferedCommandsBeforeInit = new ArrayDeque<>();
    }

    @Override
    public Receive<ChannelEntityCommand> createReceive() {
        return newReceiveBuilder()
                // 메시지 동기화 / 조회
                .onMessage(SyncRecentMessages.class, this::onSyncRecentMessages)
                .onMessage(RequestSyncMessages.class, this::onRequestSyncMessages)
                .onMessage(ResolveHistory.class, this::onResolveHistory)

                // Reader 등록 / 해제
                .onMessage(RegisterReader.class, this::onRegisterReader)
                .onMessage(RemoveShutdownReader.class, this::onRemoveShutdownReader)

                // 메시지 쓰기(보내기 / 수정 / 삭제) 요청
                .onMessage(SendMessage.class, this::onSendMessage)
                .onMessage(UpdateMessage.class, this::onUpdateMessage)
                .onMessage(DeleteMessage.class, this::onDeleteMessage)

                // 저장소로부터의 메시지 반영(Sync)
                .onMessage(SyncPersistedMessage.class, this::onSyncPersistedMessage)
                .onMessage(SyncUpdatedMessage.class, this::onSyncUpdatedMessage)
                .onMessage(SyncDeletedMessage.class, this::onSyncDeletedMessage)

                // 채널 로딩 / 에러 처리
                .onMessage(ChannelLoaded.class, this::onChannelLoaded)
                .onMessage(ChannelLoadFailed.class, this::onChannelLoadFailed)
                .onMessage(ChannelNotFound.class, this::onChannelNotFound)

                // 배치 처리
                .onMessage(ChannelMembershipBatchPersisted.class, this::onChannelMembershipBatchPersisted)
                .onMessage(ChannelBatchPersisted.class, this::onChannelBatchPersisted)

                // 채널 도메인 비즈니스 로직 처리
                .onMessage(ApplyChannelNameEdited.class, this::onApplyChannelNameEdited)
                .onMessage(ApplyChannelPolicyChanged.class, this::onApplyChannelPolicyChanged)
                .onMessage(ApplyChannelDeleted.class, this::onApplyChannelDeleted)
                .onMessage(ApplyUserJoined.class, this::onApplyUserJoined)
                .onMessage(ApplyUserInvited.class, this::onApplyUserInvited)
                .onMessage(ApplyMemberLeft.class, this::onApplyMemberLeft)
                .onMessage(ApplyMemberKicked.class, this::onApplyMemberKicked)
                .onMessage(ApplyPromotedToManager.class, this::onApplyPromotedToManager)
                .onMessage(ApplyDemotedToMember.class, this::onApplyDemotedToMember)
                .onMessage(ApplyPermissionAdded.class, this::onApplyPermissionAdded)
                .onMessage(ApplyPermissionRemoved.class, this::onApplyPermissionRemoved)

                .build();
    }

    // 채팅 메시지 동기화 / 조회 핸들러
    private Behavior<ChannelEntityCommand> onSyncRecentMessages(SyncRecentMessages command) {
        this.messages.syncMessages(command.messages());
        return this;
    }

    private Behavior<ChannelEntityCommand> onRequestSyncMessages(RequestSyncMessages command) {
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

    // Reader 관리 핸들러
    private Behavior<ChannelEntityCommand> onRegisterReader(RegisterReader command) {
        readers.put(command.readerName(), command.reader());
        return this;
    }

    private Behavior<ChannelEntityCommand> onRemoveShutdownReader(RemoveShutdownReader command) {
        readers.remove(command.readerName());
        return this;
    }

    // 채팅 메시지 커맨드(보내기 / 수정 / 삭제) 요청 핸들러
    private Behavior<ChannelEntityCommand> onSendMessage(SendMessage command) {
        ChatMessage message = createChatMessage(command);

        messageStoragePort.store(message, getContext().getSelf());
        return this;
    }

    private Behavior<ChannelEntityCommand> onUpdateMessage(UpdateMessage command) {
        messageStoragePort.update(command.messageId(), command.updatedMessage(), getContext().getSelf());
        return this;
    }

    private Behavior<ChannelEntityCommand> onDeleteMessage(DeleteMessage command) {
        messageStoragePort.delete(command.messageId(), getContext().getSelf());
        return this;
    }

    // 새롭게 보낸 채팅 메시지 동기화 반영 핸들러
    private Behavior<ChannelEntityCommand> onSyncPersistedMessage(SyncPersistedMessage command) {
        messages.add(command.message());
        readers.values()
               .forEach(reader -> reader.tell(new SyncNewMessage(command.message())));
        return this;
    }

    private Behavior<ChannelEntityCommand> onSyncUpdatedMessage(SyncUpdatedMessage command) {
        LocalDateTime updatedTimestamp = LocalDateTime.now(clock);

        messages.update(command.messageId(), command.updatedMessage(), updatedTimestamp);
        readers.values()
               .forEach(reader -> reader.tell(
                       new SyncUpdate(command.messageId(), command.updatedMessage(), updatedTimestamp)
               ));
        return this;
    }

    private Behavior<ChannelEntityCommand> onSyncDeletedMessage(SyncDeletedMessage command) {
        messages.delete(command.messageId());
        readers.values()
               .forEach(reader -> reader.tell(new SyncDeletion(command.messageId())));
        return this;
    }

    // 채널 동기화 / 에러 핸들러
    private Behavior<ChannelEntityCommand> onChannelLoaded(ChannelLoaded command) {
        if (channelState.isInitialized()) {
            return this;
        }

        channelState.initialize((command.channel().copy()), true);
        flushPendingCommands();
        tryStartChannelBatch();
        return this;
    }

    private Behavior<ChannelEntityCommand> onChannelNotFound(ChannelNotFound command) {
        return Behaviors.stopped();
    }

    private Behavior<ChannelEntityCommand> onChannelLoadFailed(ChannelLoadFailed command) {
        return Behaviors.stopped();
    }

    // 배치 핸들러
    private Behavior<ChannelEntityCommand> onChannelMembershipBatchPersisted(ChannelMembershipBatchPersisted command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        handleBatchPersisted(command.batchId(), command.success());
        return this;
    }

    private Behavior<ChannelEntityCommand> onChannelBatchPersisted(ChannelBatchPersisted command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        handleBatchPersisted(command.batchId(), command.success());
        return this;
    }

    // 채널 도메인 비즈니스 로직 관련 핸들러
    private Behavior<ChannelEntityCommand> onApplyChannelNameEdited(ApplyChannelNameEdited command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        if (committed == null || !committed.canEditName(command.changerId())) {
            return this;
        }

        ChannelNameEdited event = new ChannelNameEdited(
                command.channelId(),
                command.changerId().getValue(),
                command.newName(),
                command.occurredAt()
        );
        enqueueEvent(event, entity -> {
            Channel committedState = entity.channelState.committed();
            int membershipCount = committedState.getMemberships().size();

            entity.sendToAll(new NotifyEditChannelName(committedState.getName()));
            entity.sendToAll(new NotifyChangeChannelPolicy(committedState.getChannelPolicy()));
            entity.sendToAll(
                    new SyncChannelMetadata(
                            channelId,
                            committedState.getName(),
                            committedState.getChannelPolicy(),
                            membershipCount
                    )
            );
        });
        return this;
    }

    private Behavior<ChannelEntityCommand> onApplyChannelPolicyChanged(ApplyChannelPolicyChanged command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        if (committed == null || !committed.canChangeChannelPolicy(command.changerId())) {
            return this;
        }

        ChannelPolicyChanged event = new ChannelPolicyChanged(
                command.channelId(),
                command.changerId().getValue(),
                command.canEditOwnMessage(),
                command.canDeleteOwnMessage(),
                command.isPublic(),
                command.occurredAt()
        );
        enqueueEvent(event, entity -> {
            Channel committedState = entity.channelState.committed();
            int membershipCount = committedState.getMemberships().size();

            entity.sendToAll(new NotifyEditChannelName(committedState.getName()));
            entity.sendToAll(new NotifyChangeChannelPolicy(committedState.getChannelPolicy()));
            entity.sendToAll(
                    new SyncChannelMetadata(
                            channelId,
                            committedState.getName(),
                            committedState.getChannelPolicy(),
                            membershipCount
                    )
            );
        });
        return this;
    }

    private Behavior<ChannelEntityCommand> onApplyChannelDeleted(ApplyChannelDeleted command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        if (committed == null || !committed.canDeleteChannel(command.deleterId())) {
            return this;
        }

        sendToAll(new NotifyChannelDeleted());
        return Behaviors.stopped();
    }

    private Behavior<ChannelEntityCommand> onApplyUserJoined(ApplyUserJoined command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        if (committed == null || !committed.canJoinUser(command.userId())) {
            return this;
        }

        UserJoined event = new UserJoined(
                command.channelId(),
                command.userId().getValue(),
                command.role(),
                command.managerPermissions(),
                command.joinedAt()
        );
        enqueueEvent(event, entity -> {
            Channel committedState = entity.channelState.committed();
            int membershipCount = committedState.getMemberships().size();

            entity.notifyMembershipChange(command.userId().getValue(), committedState, membershipCount);
            entity.sendToAll(
                    new SyncChannelMetadata(
                            channelId,
                            committedState.getName(),
                            committedState.getChannelPolicy(),
                            membershipCount
                    )
            );
            entity.sendToAll(new NotifyMembershipCountChanged(membershipCount));
        });
        return this;
    }

    private Behavior<ChannelEntityCommand> onApplyUserInvited(ApplyUserInvited command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        if (committed == null
                || !committed.canInviteMember(command.inviterId(),
                                              command.inviteeId())) {
            return this;
        }

        UserInvited event = new UserInvited(
                command.channelId(),
                command.inviteeId().getValue(),
                command.role(),
                command.managerPermissions(),
                command.joinedAt()
        );
        enqueueEvent(event, entity -> {
            Channel committedState = entity.channelState.committed();
            int membershipCount = committedState.getMemberships().size();

            entity.notifyMembershipChange(command.inviteeId().getValue(), committedState, membershipCount);
            entity.sendToAll(
                    new SyncChannelMetadata(
                            channelId,
                            committedState.getName(),
                            committedState.getChannelPolicy(),
                            membershipCount
                    )
            );
            entity.sendToAll(new NotifyMembershipCountChanged(membershipCount));
        });
        return this;
    }

    private Behavior<ChannelEntityCommand> onApplyMemberLeft(ApplyMemberLeft command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        if (committed == null || !committed.canLeaveMember(command.userId())) {
            return this;
        }

        MemberLeft event = new MemberLeft(
                command.channelId(),
                command.userId().getValue(),
                command.occurredAt()
        );
        enqueueEvent(event, entity -> {
            Channel committedState = entity.channelState.committed();
            int membershipCount = committedState.getMemberships().size();

            entity.sendToAll(new NotifyMemberLeft(command.userId().getValue()));
            entity.sendToAll(
                    new SyncChannelMetadata(
                            channelId,
                            committedState.getName(),
                            committedState.getChannelPolicy(),
                            membershipCount
                    )
            );
            entity.sendToAll(new NotifyMembershipCountChanged(membershipCount));
        });
        return this;
    }

    private Behavior<ChannelEntityCommand> onApplyMemberKicked(ApplyMemberKicked command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        if (committed == null
                || !committed.canKickMember(command.executorId(),
                                            command.targetUserId())) {
            return this;
        }

        MemberKicked event = new MemberKicked(
                command.channelId(),
                command.targetUserId().getValue(),
                command.occurredAt()
        );
        enqueueEvent(event, entity -> {
            Channel committedState = entity.channelState.committed();
            int membershipCount = committedState.getMemberships().size();

            entity.sendToAll(new NotifyKickedMember(command.targetUserId().getValue()));
            entity.sendToAll(
                    new SyncChannelMetadata(
                            channelId,
                            committedState.getName(),
                            committedState.getChannelPolicy(),
                            membershipCount
                    )
            );
            entity.sendToAll(new NotifyMembershipCountChanged(membershipCount));
        });
        return this;
    }

    private Behavior<ChannelEntityCommand> onApplyPromotedToManager(ApplyPromotedToManager command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        if (committed == null
                || !committed.canPromoteToManager(command.executorId(),
                                                  command.targetUserId())) {
            return this;
        }

        PromotedToManager event = new PromotedToManager(
                command.targetUserId().getValue(),
                command.managerPermissions(),
                command.occurredAt()
        );
        enqueueEvent(event, entity -> {
            Channel committedState = entity.channelState.committed();
            int membershipCount = committedState.getMemberships().size();

            entity.notifyMembershipChange(command.targetUserId().getValue(), committedState, membershipCount);
        });
        return this;
    }

    private Behavior<ChannelEntityCommand> onApplyDemotedToMember(ApplyDemotedToMember command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        if (committed == null
                || !committed.canDemoteToMember(command.executorId(),
                                                command.targetUserId())) {
            return this;
        }

        DemotedToMember event = new DemotedToMember(
                command.channelId(),
                command.targetUserId().getValue(),
                command.occurredAt()
        );
        enqueueEvent(event, entity -> {
            Channel committedState = entity.channelState.committed();
            int membershipCount = committedState.getMemberships().size();

            entity.notifyMembershipChange(command.targetUserId().getValue(), committedState, membershipCount);
        });
        return this;
    }

    private Behavior<ChannelEntityCommand> onApplyPermissionAdded(ApplyPermissionAdded command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        ChannelPermissionType permission = ChannelPermissionType.valueOf(command.permissionType());
        if (committed == null
                || !committed.canAddPermission(command.grantorId(),
                                               command.granteeId(),
                                               permission)) {
            return this;
        }

        PermissionAdded event = new PermissionAdded(
                command.channelId(),
                command.granteeId().getValue(),
                command.permissionType(),
                command.occurredAt()
        );
        enqueueEvent(event, entity -> {
            Channel committedState = entity.channelState.committed();
            int membershipCount = committedState.getMemberships().size();

            entity.notifyMembershipChange(command.granteeId().getValue(), committedState, membershipCount);
        });
        return this;
    }

    private Behavior<ChannelEntityCommand> onApplyPermissionRemoved(ApplyPermissionRemoved command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        Channel committed = channelState.committed();
        ChannelPermissionType permission = ChannelPermissionType.valueOf(command.permissionType());
        if (committed == null
                || !committed.canRemovePermission(command.grantorId(),
                                                  command.granteeId(),
                                                  permission)) {
            return this;
        }

        PermissionRemoved event = new PermissionRemoved(
                command.channelId(),
                command.granteeId().getValue(),
                command.permissionType(),
                command.occurredAt()
        );
        enqueueEvent(event, entity -> {
            Channel committedState = entity.channelState.committed();
            int membershipCount = committedState.getMemberships().size();

            entity.notifyMembershipChange(command.granteeId().getValue(), committedState, membershipCount);
        });
        return this;
    }

    private void enqueueEvent(ChannelDomainEvent event, Consumer<ChannelEntity> notification) {
        pendingChannelEvents.add(event);
        pendingNotifications.add(notification);
        tryStartChannelBatch();
    }

    // 배치 처리 관련 메서드
    private void flushPendingCommands() {
        while (!bufferedCommandsBeforeInit.isEmpty()) {
            getContext().getSelf()
                        .tell(bufferedCommandsBeforeInit.pollFirst());
        }
    }

    private void tryStartChannelBatch() {
        if (!canStartBatch()) {
            return;
        }

        channelBatchRunning = true;

        long batchId = batchSequenceGenerator.nextSequence();
        List<ChannelDomainEvent> batch = List.copyOf(pendingChannelEvents);
        pendingChannelEvents.clear();

        List<Consumer<ChannelEntity>> batchNotifications = collectBatchNotifications(batch.size());

        try {
            applyEventsToUncommitted(batch);

            List<ChannelDomainEvent> membershipEvents = filterMembershipEvents(batch);
            List<ChannelDomainEvent> channelEvents = filterChannelEvents(batch);

            int operations = countPersistOperations(membershipEvents, channelEvents);
            if (operations == 0) {
                completeBatchWithNoOperations(batchId);
                return;
            }

            channelBatchStates.put(batchId, new ChannelBatchState(operations, batch, batchNotifications));

            persistBatches(batchId, channelEvents, membershipEvents);
        } catch (Exception e) {
            sendBatchFailureMessage(batchId, batch, e);
        }
    }

    private boolean canStartBatch() {
        return channelState.isInitialized() && !channelBatchRunning && !pendingChannelEvents.isEmpty();
    }

    private List<Consumer<ChannelEntity>> collectBatchNotifications(int size) {
        List<Consumer<ChannelEntity>> notifications = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            notifications.add(pendingNotifications.pollFirst());
        }

        return notifications;
    }

    private List<ChannelDomainEvent> filterMembershipEvents(List<ChannelDomainEvent> batch) {
        return batch.stream()
                    .filter(ChannelDomainEvent::isMembershipEvent)
                    .toList();
    }

    private List<ChannelDomainEvent> filterChannelEvents(List<ChannelDomainEvent> batch) {
        return batch.stream()
                    .filter(event -> !event.isMembershipEvent())
                    .toList();
    }

    private int countPersistOperations(List<ChannelDomainEvent> membershipEvents,
            List<ChannelDomainEvent> channelEvents) {
        int count = 0;
        if (!channelEvents.isEmpty()) {
            count++;
        }
        if (!membershipEvents.isEmpty()) {
            count++;
        }

        return count;
    }

    private void completeBatchWithNoOperations(long batchId) {
        channelBatchRunning = false;
        handleBatchPersisted(batchId, true);
    }

    private void persistBatches(
            long batchId,
            List<ChannelDomainEvent> channelEvents,
            List<ChannelDomainEvent> membershipEvents
    ) {
        if (!channelEvents.isEmpty()) {
            persistChannelBatch(batchId, channelEvents, channelState.working());
        }
        if (!membershipEvents.isEmpty()) {
            persistMembershipBatch(batchId, membershipEvents, channelState.working());
        }
    }

    private void sendBatchFailureMessage(long batchId, List<ChannelDomainEvent> batch, Exception e) {
        getContext().getSelf()
                    .tell(new ChannelBatchPersisted(batchId, batch, false, e.getMessage()));
    }


    private void applyEventsToUncommitted(List<ChannelDomainEvent> batch) {
        for (ChannelDomainEvent event : batch) {
            event.apply(channelState.working());
        }
    }

    private void persistChannelBatch(long batchId, List<ChannelDomainEvent> batch, Channel channelToPersist) {
        channelActorStoragePort.update(channelToPersist, getContext().getSelf(), batchId, batch);
    }

    private void persistMembershipBatch(long batchId, List<ChannelDomainEvent> batch, Channel channelToPersist) {
        try {
            for (ChannelDomainEvent event : batch) {
                event.persistMembership(channelMembershipActorStoragePort, channelToPersist);
            }
            getContext().getSelf()
                        .tell(new ChannelMembershipBatchPersisted(batchId, batch, true));
        } catch (Exception e) {
            getContext().getSelf()
                        .tell(new ChannelMembershipBatchPersisted(batchId, batch, false));
        }
    }

    private void handleBatchPersisted(long batchId, boolean success) {
        ChannelBatchState state = channelBatchStates.get(batchId);
        if (state == null) {
            return;
        }

        updateBatchSuccessFlag(state, success);
        if (hasPendingOperations(state)) {
            return;
        }

        finalizeBatchState(state);
        cleanupBatch(batchId);
        tryStartChannelBatch();
    }

    private void updateBatchSuccessFlag(ChannelBatchState state, boolean success) {
        if (!success) {
            state.success = false;
        }
    }

    private boolean hasPendingOperations(ChannelBatchState state) {
        state.pendingOperations--;
        return state.pendingOperations > 0;
    }

    private void finalizeBatchState(ChannelBatchState state) {
        if (state.success) {
            commitBatch(state);
            return;
        }
        rollbackBatch();
    }

    private void commitBatch(ChannelBatchState state) {
        channelState.commitWorking();
        if (state.notifications != null) {
            state.notifications.forEach(notification -> notification.accept(this));
        }
    }

    private void rollbackBatch() {
        channelState.resetWorking();
    }

    private void cleanupBatch(long batchId) {
        channelBatchStates.remove(batchId);
        channelBatchRunning = false;
    }

    private boolean enqueueUntilInitialized(ChannelEntityCommand command) {
        if (channelState.isInitialized()) {
            return false;
        }

        bufferedCommandsBeforeInit.add(command);
        return true;
    }

    private void sendToAll(ChannelReaderCommand command) {
        readers.values().forEach(reader -> reader.tell(command));
    }

    private void notifyMembershipChange(Long userId, Channel committed, int membershipCount) {
        ChannelMembership membership = committed.getMemberships().get(UserId.create(userId));

        if (membership == null) {
            return;
        }

        sendToAll(new SyncMembership(userId, membership, membershipCount));
        sendToAll(new NotifyChangeChannelMembership(userId, membership, membershipCount));
    }

    // 도메인 관련 메서드
    private ChatMessage createChatMessage(SendMessage command) {
        long messageSequence = messageSequenceGenerator.nextSequence();

        return ChatMessage.create(
                channelId,
                command.userId(),
                messageSequence,
                command.message(),
                LocalDateTime.now(clock),
                LocalDateTime.now(clock)
        );
    }

    // 채널 상태 관리 Holder
    private static class ChannelStateHolder {

        private Channel committed;
        private Channel working;
        private boolean loaded;
        private boolean initialized;

        private ChannelStateHolder() {
            this.committed = null;
            this.working = null;
            this.loaded = false;
            this.initialized = false;
        }

        private static ChannelStateHolder uninitialized() {
            return new ChannelStateHolder();
        }

        private Channel committed() {
            return committed;
        }

        private Channel working() {
            return working;
        }

        private boolean isLoaded() {
            return loaded;
        }

        private boolean isInitialized() {
            return initialized;
        }

        private void initialize(Channel committed, boolean loaded) {
            this.committed = committed;
            this.working = committed.copy();
            this.loaded = loaded;
            this.initialized = true;
        }

        private void commitWorking() {
            this.committed = this.working;
            resetWorking();
        }

        private void resetWorking() {
            this.working = this.committed.copy();
        }
    }

    // 배치 상태
    private static class ChannelBatchState {

        private int pendingOperations;
        private boolean success;
        private final List<ChannelDomainEvent> events;
        private final List<Consumer<ChannelEntity>> notifications;

        private ChannelBatchState(
                int pendingOperations,
                List<ChannelDomainEvent> events,
                List<Consumer<ChannelEntity>> notifications
        ) {
            this.pendingOperations = pendingOperations;
            this.success = true;
            this.events = events;
            this.notifications = notifications;
        }
    }

    // 채팅 히스토리 동기화를 요청하는 메시지 : ChannelReaderActor -> ChannelEntity
    record RequestSyncMessages(ActorRef<ChannelReaderCommand> secondary) implements ChannelEntityCommand { }
}
