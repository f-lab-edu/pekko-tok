package com.tok.pekko.global.actor;

import com.tok.pekko.adapter.out.websocket.ChannelReaderRegistryActor;
import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.adapter.out.websocket.ClientSessionActor;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipPort;
import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.global.actor.GuardianActor.GuardianCommand;
import com.tok.pekko.global.common.CborSerializable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;

public class GuardianActor extends AbstractBehavior<GuardianCommand> {

    private final ActorRef<ChannelReaderRegistryCommand> readerRegistry;

    public static Behavior<GuardianCommand> create() {
        return Behaviors.setup(GuardianActor::new);
    }

    private GuardianActor(ActorContext<GuardianCommand> context) {
        super(context);

        ClusterSharding clusterSharding = ClusterSharding.get(context.getSystem());

        this.readerRegistry = context.spawn(
                ChannelReaderRegistryActor.create(clusterSharding),
                "channel-reader-registry-actor"
        );
    }

    @Override
    public Receive<GuardianCommand> createReceive() {
        return newReceiveBuilder().onMessage(SpawnClientSession.class, this::onSpawnClientSession)
                                  .build();
    }

    private Behavior<GuardianCommand> onSpawnClientSession(SpawnClientSession command) {
        ActorRef<ClientSessionCommand> clientSession = getContext().spawn(
                ClientSessionActor.create(command.userId(), command.clientMessageSender(),
                        command.channelMembershipPort(), readerRegistry),
                "client-session-" + System.nanoTime() + "-" + command.userId()
        );

        command.replyTo()
               .tell(new SpawnedClientSession(clientSession));
        return this;
    }

    public interface GuardianCommand extends CborSerializable { }

    public record SpawnClientSession(Long userId, ClientMessageSender clientMessageSender, ChannelMembershipPort channelMembershipPort, ActorRef<GuardianCommand> replyTo) implements GuardianCommand { }
    public record SpawnedClientSession(ActorRef<ClientSessionCommand> clientSession) implements GuardianCommand { }
}
