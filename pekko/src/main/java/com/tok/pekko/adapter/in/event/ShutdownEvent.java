package com.tok.pekko.adapter.in.event;

import com.tok.pekko.common.CborSerializable;

public record ShutdownEvent() implements CborSerializable {
}
