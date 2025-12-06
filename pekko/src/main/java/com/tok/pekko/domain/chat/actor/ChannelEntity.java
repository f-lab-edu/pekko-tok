package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelDomainEvent;
import com.tok.pekko.domain.chat.actor.ChannelReaderActor.DeliverSyncMessages;
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
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncNewMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.SyncUpdate;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
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
    private final Deque<ChannelDomainEvent> pendingChannelEvents;

    // 채널 / 멤버십 DB 영속화 Port
    private final ChannelActorStoragePort channelActorStoragePort;
    private final ChannelMembershipActorStoragePort channelMembershipActorStoragePort;

    // Reader 관리
    private final Map<String, ActorRef<ChannelReaderCommand>> readers;

    // 명령 버퍼링 / 배치 관련 상태
    private final Deque<ChannelEntityCommand> bufferedCommandsBeforeInit;
    private boolean channelBatchRunning;
    private long channelBatchSequence;
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

        // 채널 상태 & 배치 관련
        this.channelState = channelState;
        this.pendingChannelEvents = new ArrayDeque<>();
        this.channelBatchRunning = false;
        this.channelBatchSequence = 0L;
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
        List<ChatMessage> history = this.messages.getHistory(
                command.messageSequence(), command.size());

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
        messageStoragePort.update(
                command.messageId(), command.updatedMessage(), getContext().getSelf());
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

    private Behavior<ChannelEntityCommand> onChannelMembershipBatchPersisted(ChannelMembershipBatchPersisted command) {
        if (enqueueUntilInitialized(command)) {
            return this;
        }

        handleBatchPersisted(command.batchId(), command.success());
        return this;
    }

    // 배치 처리 관련 메서드
    private void flushPendingCommands() {
        while (!bufferedCommandsBeforeInit.isEmpty()) {
            getContext().getSelf()
                        .tell(bufferedCommandsBeforeInit.pollFirst());
        }
    }

    private void tryStartChannelBatch() {
        if (!channelState.isInitialized() || channelBatchRunning || pendingChannelEvents.isEmpty()) {
            return;
        }

        channelBatchRunning = true;
        long batchId = ++channelBatchSequence;
        List<ChannelDomainEvent> batch = pendingChannelEvents.stream().toList();
        pendingChannelEvents.clear();

        List<Consumer<ChannelEntity>> batchNotifications = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            batchNotifications.add(pendingNotifications.pollFirst());
        }

        try {
            applyEventsToUncommitted(batch);
            List<ChannelDomainEvent> membershipEvents = batch.stream()
                                                             .filter(ChannelDomainEvent::isMembershipEvent)
                                                             .toList();
            List<ChannelDomainEvent> channelEvents = batch.stream()
                                                          .filter(event -> !event.isMembershipEvent())
                                                          .toList();

            int operations = 0;
            if (!channelEvents.isEmpty()) operations++;
            if (!membershipEvents.isEmpty()) operations++;

            if (operations == 0) {
                channelBatchRunning = false;
                handleBatchPersisted(batchId, true);
                return;
            }

            channelBatchStates.put(batchId, new ChannelBatchState(operations, batch, batchNotifications));

            if (!channelEvents.isEmpty()) {
                persistChannelBatch(batchId, channelEvents, channelState.working());
            }
            if (!membershipEvents.isEmpty()) {
                persistMembershipBatch(batchId, membershipEvents, channelState.working());
            }
        } catch (Exception e) {
            getContext().getSelf()
                        .tell(new ChannelBatchPersisted(batchId, batch, false, e.getMessage()));
        }
    }

    private void applyEventsToUncommitted(List<ChannelDomainEvent> batch) {
        for (ChannelDomainEvent event : batch) {
            event.apply(channelState.working());
        }
    }

    private void persistChannelBatch(
            long batchId, List<ChannelDomainEvent> batch, Channel channelToPersist) {
        channelActorStoragePort.update(channelToPersist, getContext().getSelf(), batchId, batch);
    }

    private void persistMembershipBatch(
            long batchId, List<ChannelDomainEvent> batch, Channel channelToPersist) {
        try {
            for (ChannelDomainEvent event : batch) {
                event.persistMembership(channelMembershipActorStoragePort, channelToPersist);
            }
            getContext().getSelf()
                        .tell(new ChannelMembershipBatchPersisted(batchId, batch, true, ""));
        } catch (Exception e) {
            getContext().getSelf()
                        .tell(new ChannelMembershipBatchPersisted(batchId, batch, false, e.getMessage()));
        }
    }

    private void handleBatchPersisted(long batchId, boolean success) {
        ChannelBatchState state = channelBatchStates.get(batchId);
        if (state == null) {
            return;
        }

        if (!success) {
            state.success = false;
        }

        state.pendingOperations--;
        if (state.pendingOperations > 0) {
            return;
        }

        if (state.success) {
            channelState.commitWorking();
            if (state.notifications != null) {
                state.notifications.forEach(notification -> notification.accept(this));
            }
        } else {
            channelState.resetWorking();
        }

        channelBatchStates.remove(batchId);
        channelBatchRunning = false;
        tryStartChannelBatch();
    }

    private boolean enqueueUntilInitialized(ChannelEntityCommand command) {
        if (channelState.isInitialized()) {
            return false;
        }

        bufferedCommandsBeforeInit.add(command);
        return true;
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
