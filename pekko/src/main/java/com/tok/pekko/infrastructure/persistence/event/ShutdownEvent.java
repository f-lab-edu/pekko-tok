package com.tok.pekko.infrastructure.persistence.event;

import com.tok.pekko.common.CborSerializable;

public record ShutdownEvent() implements CborSerializable {
}
