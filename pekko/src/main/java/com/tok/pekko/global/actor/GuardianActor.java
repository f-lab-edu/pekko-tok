package com.tok.pekko.global.actor;

import com.tok.pekko.adapter.out.websocket.ChannelReaderRegistryActor;
import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.adapter.out.websocket.ClientSessionActor;
import com.tok.pekko.adapter.out.websocket.InviteUserEventListenerActor;
import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity;
import com.tok.pekko.domain.chat.actor.ChatMessages;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorMessagePort;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.InviteUserEventProtocol.InviteUserEventCommand;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.global.actor.GuardianActor.GuardianCommand;
import com.tok.pekko.global.common.CborSerializable;
import java.time.Clock;
import java.time.Duration;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.pubsub.Topic;
import org.apache.pekko.actor.typed.pubsub.Topic.Command;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;

public class GuardianActor extends AbstractBehavior<GuardianCommand> {

    public static Behavior<GuardianCommand> create(
            Clock clock,
            MessageStoragePort messageStoragePort,
            ChannelActorStoragePort channelActorStoragePort,
            ChannelMembershipActorStoragePort channelMembershipActorStoragePort
    ) {
        return Behaviors.setup(
                context -> new GuardianActor(
                        context,
                        clock,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );
    }

    private final ActorRef<ChannelReaderRegistryCommand> readerRegistry;
    private final ActorRef<Command<InviteUserEventCommand>> inviteUserTopic;
    private ActorRef<InviteUserEventCommand> inviteUserEventListener;

    private GuardianActor(
            ActorContext<GuardianCommand> context,
            Clock clock,
            MessageStoragePort messageStoragePort,
            ChannelActorStoragePort channelActorStoragePort,
            ChannelMembershipActorStoragePort channelMembershipActorStoragePort
    ) {
        super(context);

        ClusterSharding clusterSharding = ClusterSharding.get(context.getSystem());

        this.readerRegistry = context.spawn(
                ChannelReaderRegistryActor.create(clusterSharding, Duration.ofSeconds(240L)),
                "channel-reader-registry-actor"
        );

        this.inviteUserTopic = context.spawn(
                Topic.create(InviteUserEventCommand.class, "user-channel-events"),
                "user-channel-topic"
        );
        this.inviteUserEventListener = null;

        clusterSharding.init(
                Entity.of(
                        ChannelEventHandlerEntity.ENTITY_TYPE_KEY,
                        entityContext -> ChannelEventHandlerEntity.create(
                                clusterSharding,
                                Long.valueOf(entityContext.getEntityId()),
                                messageStoragePort,
                                channelActorStoragePort,
                                channelMembershipActorStoragePort
                        )
                )
        );
        clusterSharding.init(
                Entity.of(
                        ChannelEntity.ENTITY_TYPE_KEY,
                        entityContext -> ChannelEntity.create(
                                clock,
                                Long.valueOf(entityContext.getEntityId()),
                                new ChatMessages(),
                                clusterSharding,
                                messageStoragePort,
                                channelActorStoragePort,
                                inviteUserTopic
                        )
                )
        );
    }

    @Override
    public Receive<GuardianCommand> createReceive() {
        return newReceiveBuilder().onMessage(SpawnClientSession.class, this::onSpawnClientSession)
                                  .onMessage(InitializeInviteUserListener.class, this::onInitializeInviteUserListener)
                                  .build();
    }

    private Behavior<GuardianCommand> onSpawnClientSession(SpawnClientSession command) {
        ActorRef<ClientSessionCommand> clientSession = getContext().spawn(
                Behaviors.supervise(
                        ClientSessionActor.create(
                                command.userId(),
                                command.clientMessageSender(),
                                command.messageStoragePort(),
                                command.channelMembershipActorMessagePort(),
                                readerRegistry
                        )
                ).onFailure(SupervisorStrategy.restart()),
                "client-session-" + System.nanoTime() + "-" + command.userId()
        );

        command.replyTo()
               .tell(new SpawnedClientSession(clientSession));
        return this;
    }

    private Behavior<GuardianCommand> onInitializeInviteUserListener(InitializeInviteUserListener command) {
        if (inviteUserEventListener != null) {
            return this;
        }

        inviteUserEventListener = getContext().spawn(
                InviteUserEventListenerActor.create(inviteUserTopic, command.clientSessionActorManagementService()),
                "invite-user-event-listener-actor"
        );

        return this;
    }

    public interface GuardianCommand extends CborSerializable { }

    // Client WebSocket Session 연결 시 외부로부터 ClientSessionActor Spawn을 요청하기 위한 메시지 : ClientSessionActorManagementService -> GuardianActor
    public record SpawnClientSession(Long userId, ClientMessageSender clientMessageSender, MessageStoragePort messageStoragePort, ChannelMembershipActorMessagePort channelMembershipActorMessagePort, ActorRef<GuardianCommand> replyTo) implements GuardianCommand { }

    // ClientSessionActor spawn 완료 후 ActorRef를 ClientSessionActorManagementService.AskPattern에 전달하기 위한 메시지 : ClientSessionActorManagementService -> GuardianActor
    public record SpawnedClientSession(ActorRef<ClientSessionCommand> clientSession) implements GuardianCommand { }

    // InviteUserEventListenerActor를 초기화하기 위한 메시지 : 외부 -> GuardianActor
    public record InitializeInviteUserListener(ClientSessionActorManagementService clientSessionActorManagementService) implements GuardianCommand { }
}
