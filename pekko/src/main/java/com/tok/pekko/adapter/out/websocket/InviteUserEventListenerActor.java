package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.domain.chat.port.out.InviteUserEventProtocol.InviteUserEventCommand;
import com.tok.pekko.domain.chat.port.out.InviteUserEventProtocol.Invited;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.pubsub.Topic;
import org.apache.pekko.actor.typed.pubsub.Topic.Command;

public class InviteUserEventListenerActor extends AbstractBehavior<InviteUserEventCommand> {

    private final ClientSessionActorManagementService clientSessionActorManagementService;

    public static Behavior<InviteUserEventCommand> create(
            ActorRef<Command<InviteUserEventCommand>> inviteUserTopic,
            ClientSessionActorManagementService clientSessionActorManagementService
    ) {
        return Behaviors.setup(context -> new InviteUserEventListenerActor(
                context,
                inviteUserTopic,
                clientSessionActorManagementService)
        );
    }

    public InviteUserEventListenerActor(
            ActorContext<InviteUserEventCommand> context,
            ActorRef<Command<InviteUserEventCommand>> inviteUserTopic,
            ClientSessionActorManagementService clientSessionActorManagementService
    ) {
        super(context);

        this.clientSessionActorManagementService = clientSessionActorManagementService;

        inviteUserTopic.tell(Topic.subscribe(context.getSelf()));
    }

    @Override
    public Receive<InviteUserEventCommand> createReceive() {
        return newReceiveBuilder().onMessage(Invited.class, this::onInvited)
                                  .build();
    }

    private Behavior<InviteUserEventCommand> onInvited(Invited command) {
        clientSessionActorManagementService.syncInvitedChannel(command.channelId(), command.inviteeId());

        return this;
    }
}
