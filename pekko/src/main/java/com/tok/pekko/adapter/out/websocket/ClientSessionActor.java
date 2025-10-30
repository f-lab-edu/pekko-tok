package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverNewMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverUpdatedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class ClientSessionActor extends AbstractBehavior<ClientSessionCommand> {

    private final ClientMessageSender clientMessageSender;
    private final Map<Long, ActorRef<ChatChannelReaderCommand>> readers;

    public static Behavior<ClientSessionCommand> create(ClientMessageSender clientMessageSender) {
        return Behaviors.setup(context -> new ClientSessionActor(context, clientMessageSender));
    }

    private ClientSessionActor(ActorContext<ClientSessionCommand> context, ClientMessageSender clientMessageSender) {
        super(context);

        this.clientMessageSender = clientMessageSender;
        this.readers = new HashMap<>();
    }

    @Override
    public Receive<ClientSessionCommand> createReceive() {
        return newReceiveBuilder().onMessage(DeliverNewMessage.class, this::onDeliverNewMessage)
                                  .onMessage(DeliverUpdatedMessage.class, this::onDeliverUpdatedMessage)
                                  .onMessage(DeliverDeletedMessage.class, this::onDeliverDeletedMessage)
                                  .onMessage(DeliverHistory.class, this::onDeliverHistory)
                                  .onMessage(Shutdown.class, this::onShutdown)
                                  .onMessage(FoundChannelReaders.class, this::onFoundChannelReaders)
                                  .build();
    }

    private Behavior<ClientSessionCommand> onDeliverNewMessage(DeliverNewMessage command) {
        clientMessageSender.sendMessage(command.message());

        return this;
    }

    private Behavior<ClientSessionCommand> onDeliverUpdatedMessage(DeliverUpdatedMessage command) {
        clientMessageSender.sendMessage(command.updatedMessage());

        return this;
    }

    private Behavior<ClientSessionCommand> onDeliverDeletedMessage(DeliverDeletedMessage command) {
        clientMessageSender.sendDeletedMessage(command.deletedMessage());

        return this;
    }

    private Behavior<ClientSessionCommand> onDeliverHistory(DeliverHistory command) {
        clientMessageSender.sendMessages(command.messages());

        return this;
    }

    private Behavior<ClientSessionCommand> onShutdown(Shutdown command) {
        return Behaviors.stopped();
    }

    private Behavior<ClientSessionCommand> onFoundChannelReaders(FoundChannelReaders command) {
        readers.putAll(command.chatChannelReaderRefs());

        return this;
    }

    public record FoundChannelReaders(Map<Long, ActorRef<ChatChannelReaderCommand>> chatChannelReaderRefs) implements ClientSessionCommand { }
}
