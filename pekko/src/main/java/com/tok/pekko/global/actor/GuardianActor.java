package com.tok.pekko.global.actor;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class GuardianActor extends AbstractBehavior<SpawnProtocol.Command> {

    public static Behavior<SpawnProtocol.Command> create() {
        return Behaviors.setup(GuardianActor::new);
    }

    private GuardianActor(ActorContext<SpawnProtocol.Command> context) {
        super(context);
    }

    @Override
    public Receive<SpawnProtocol.Command> createReceive() {
        return newReceiveBuilder().build();
    }
}
