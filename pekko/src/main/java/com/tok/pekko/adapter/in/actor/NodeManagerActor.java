package com.tok.pekko.adapter.in.actor;

import com.tok.pekko.adapter.in.event.RegisteredEvent;
import com.tok.pekko.adapter.in.event.ShutdownEvent;
import com.tok.pekko.adapter.in.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.model.ChatChannelEntity;
import com.tok.pekko.domain.chat.model.ChatChannelReaderActor;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RegisterReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RequestJoin;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.in.NodeManagerProtocol.NodeManagerActorCommand;
import com.tok.pekko.domain.chat.port.in.NodeManagerProtocol.CreateReader;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.eventstream.EventStream;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

public class NodeManagerActor extends AbstractBehavior<NodeManagerActorCommand> {

    private final ClusterSharding clusterSharding;
    private final MessageStoragePort messageStoragePort;
    private final Map<NodeReaderKey, ActorRef<ChatChannelReaderCommand>> localChatChannelReaders;

    public static Behavior<NodeManagerActorCommand> create(MessageStoragePort messageStoragePort) {
        return Behaviors.setup(context -> {
            ClusterSharding clusterSharding = ClusterSharding.get(context.getSystem());
            Map<NodeReaderKey, ActorRef<ChatChannelReaderCommand>> localChatChannelReaders = new HashMap<>();

            subscribeEventStream(context);

            return new NodeManagerActor(context, clusterSharding, messageStoragePort, localChatChannelReaders);
        });
    }

    private static void subscribeEventStream(ActorContext<NodeManagerActorCommand> context) {
        subscribeRegisteredEvent(context);
        subscribeShutdownEvent(context);
    }

    private static void subscribeRegisteredEvent(ActorContext<NodeManagerActorCommand> context) {
        ActorRef<RegisteredEvent> registeredEventAdapter = context.messageAdapter(
                RegisteredEvent.class,
                event -> new RegisterSession(event.clientMessageSender(), event.channelId(), event.userId())
        );

        context.getSystem()
               .eventStream()
               .tell(new EventStream.Subscribe<>(RegisteredEvent.class, registeredEventAdapter));
    }

    private static void subscribeShutdownEvent(ActorContext<NodeManagerActorCommand> context) {
        ActorRef<ShutdownEvent> shutdownEventAdapter = context.messageAdapter(
                ShutdownEvent.class,
                event -> new Shutdown()
        );

        context.getSystem()
               .eventStream()
               .tell(new EventStream.Subscribe<>(ShutdownEvent.class, shutdownEventAdapter));
    }

    private NodeManagerActor(
            ActorContext<NodeManagerActorCommand> context,
            ClusterSharding clusterSharding,
            MessageStoragePort messageStoragePort,
            Map<NodeReaderKey, ActorRef<ChatChannelReaderCommand>> localChatChannelReaders
    ) {
        super(context);

        this.clusterSharding = clusterSharding;
        this.messageStoragePort = messageStoragePort;
        this.localChatChannelReaders = localChatChannelReaders;
    }

    @Override
    public Receive<NodeManagerActorCommand> createReceive() {
        return newReceiveBuilder().onMessage(RegisterSession.class, this::onRegisterSession)
                                  .onMessage(CreateReader.class, this::onCreateReader)
                                  .onMessage(TerminateSession.class, this::onTerminateSession)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .build();
    }

    private Behavior<NodeManagerActorCommand> onRegisterSession(RegisterSession command) {
        ActorRef<ClientSessionCommand> clientSessionActorRef = spawnClientSessionActor(command);
        EntityRef<ChatChannelEntityCommand> chatChannelEntityRef = findChatChannelEntityRef(command.channelId());

        chatChannelEntityRef.tell(
                new RequestJoin(command.userId(), clientSessionActorRef, getContext().getSelf())
        );

        return this;
    }

    private Behavior<NodeManagerActorCommand> onCreateReader(CreateReader command) {
        NodeReaderKey key = new NodeReaderKey(command.channelId(), command.userId());
        ActorRef<ChatChannelReaderCommand> chatChannelReaerActorRef = spawnChatChannelReaderActor(command);
        ActorRef<ChatChannelReaderCommand> oldReader = localChatChannelReaders.put(key, chatChannelReaerActorRef);

        if (oldReader != null) {
            oldReader.tell(new ChatChannelReaderProtocol.Shutdown());
        }

        command.replyTo()
               .tell(new RegisterReader(command.userId(), chatChannelReaerActorRef));

        return this;
    }

    private Behavior<NodeManagerActorCommand> onTerminateSession(TerminateSession command) {
        NodeReaderKey key = new NodeReaderKey(command.channelId(), command.userId());
        ActorRef<ChatChannelReaderCommand> removeReaderRef = localChatChannelReaders.remove(key);

        if (removeReaderRef != null) {
            removeReaderSession(command.channelId(), command.channelId(), removeReaderRef);
        }

        return this;
    }

    private Behavior<NodeManagerActorCommand> onShutdown(Shutdown command) {
        localChatChannelReaders.values()
                               .forEach(reader -> reader.tell(new ChatChannelReaderProtocol.Shutdown()));

        return Behaviors.stopped();
    }

    private ActorRef<ClientSessionCommand> spawnClientSessionActor(RegisterSession command) {
        return getContext().spawn(
                ClientSessionActor.create(command.clientMessageSender()),
                "client-session-" + command.userId() + ":" + command.channelId()
        );
    }

    private ActorRef<ChatChannelReaderCommand> spawnChatChannelReaderActor(CreateReader command) {
        return getContext().spawn(
                ChatChannelReaderActor.create(
                        command.channelId(),
                        command.messages(),
                        messageStoragePort,
                        command.clientActorRef()
                ),
                "chat-channel-reader-" + System.nanoTime() + command.channelId() + ":" + command.userId()
        );
    }

    private void removeReaderSession(Long channelId, Long userId, ActorRef<ChatChannelReaderCommand> removeReaderRef) {
        removeReaderRef.tell(new ChatChannelReaderProtocol.Shutdown());

        EntityRef<ChatChannelEntityCommand> chatChannelEntityRef = findChatChannelEntityRef(channelId);

        chatChannelEntityRef.tell(new RemoveShutdownReader(userId));
    }

    private EntityRef<ChatChannelEntityCommand> findChatChannelEntityRef(Long channelId) {
        return clusterSharding.entityRefFor(
                ChatChannelEntity.ENTITY_TYPE_KEY, String.valueOf(channelId)
        );
    }

    public record RegisterSession(ClientMessageSender clientMessageSender, Long channelId, Long userId) implements NodeManagerActorCommand { }
    public record TerminateSession(Long channelId, Long userId) implements NodeManagerActorCommand { }
    public record Shutdown() implements NodeManagerActorCommand { }
}
