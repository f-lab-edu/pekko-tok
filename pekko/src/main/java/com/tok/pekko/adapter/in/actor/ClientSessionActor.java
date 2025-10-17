package com.tok.pekko.adapter.in.actor;

import com.tok.pekko.adapter.in.websocket.ClientMessageSender;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class ClientSessionActor extends AbstractBehavior<ClientSessionCommand> {

    private final ClientMessageSender clientMessageSender;

    public static Behavior<ClientSessionCommand> create(ClientMessageSender clientMessageSender) {
        return Behaviors.setup(context -> new ClientSessionActor(context, clientMessageSender));
    }

    private ClientSessionActor(ActorContext<ClientSessionCommand> context, ClientMessageSender clientMessageSender) {
        super(context);

        this.clientMessageSender = clientMessageSender;
    }

    @Override
    public Receive<ClientSessionCommand> createReceive() {
        return newReceiveBuilder().onMessage(DeliverCommand.class, this::onDeliverMessage)
                                  .onMessage(DeliverHistory.class, this::onDeliverHistory)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .build();
    }

    private Behavior<ClientSessionCommand> onDeliverMessage(DeliverCommand command) {
        clientMessageSender.sendMessage(command.message());

        return this;
    }

    private Behavior<ClientSessionCommand> onDeliverHistory(DeliverHistory command) {
        clientMessageSender.sendMessages(command.messages());

        return this;
    }

    private Behavior<ClientSessionCommand> onShutdown(Shutdown command) {
        clientMessageSender.close();

        return Behaviors.stopped();
    }
}
