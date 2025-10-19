package com.tok.pekko.infrastructure.actor;

import com.tok.pekko.adapter.in.actor.NodeManagerActor;
import com.tok.pekko.common.CborSerializable;
import com.tok.pekko.infrastructure.actor.GuardianActor.GuardianCommand;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class GuardianActor extends AbstractBehavior<GuardianCommand> {

    public static Behavior<GuardianCommand> create() {
        return Behaviors.setup(context -> {
            context.spawn(
                    NodeManagerActor.create(),
                    "node-manager-actor"
            );

            return new GuardianActor(context);
        });
    }

    private GuardianActor(ActorContext<GuardianCommand> context) {
        super(context);
    }

    @Override
    public Receive<GuardianCommand> createReceive() {
        return newReceiveBuilder().build();
    }

    public interface GuardianCommand extends CborSerializable { }
}
