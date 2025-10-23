package com.tok.pekko.global.actor;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SpawnProtocol;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

public class GuardianActor {

    public static Behavior<SpawnProtocol.Command> create() {
        return Behaviors.setup(context -> SpawnProtocol.create());
    }

    private GuardianActor() {
    }
}
