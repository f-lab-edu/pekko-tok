package com.tok.pekko.application.actor.event;

import com.tok.pekko.global.common.CborSerializable;

public record ShutdownEvent() implements CborSerializable {
}
