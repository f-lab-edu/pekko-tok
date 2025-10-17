package com.tok.pekko.adapter.in.event;

import com.tok.pekko.infrastructure.actor.CborSerializable;

public record ShutdownEvent() implements CborSerializable {
}
