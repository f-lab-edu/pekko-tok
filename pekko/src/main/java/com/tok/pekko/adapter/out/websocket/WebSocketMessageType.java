package com.tok.pekko.adapter.out.websocket;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum WebSocketMessageType {

    NEW("NEW"),
    UPDATED("UPDATED"),
    DELETED("DELETED"),
    WS_HEALTH_PING("WS_HEALTH_PING"),
    WS_RECONNECT("WS_RECONNECT"),
    CHANNEL_MEMBERSHIP_CHANGED("CHANNEL_MEMBERSHIP_CHANGED"),
    CHANNEL_MEMBERSHIP_COUNT_CHANGED("CHANNEL_MEMBERSHIP_COUNT_CHANGED"),
    CHANNEL_INVITED("CHANNEL_INVITED"),
    CHANNEL_NAME_EDITED("CHANNEL_NAME_EDITED"),
    CHANNEL_KICKED("CHANNEL_KICKED"),
    CHANNEL_DELETED("CHANNEL_DELETED"),
    CHANNEL_POLICY_CHANGED("CHANNEL_POLICY_CHANGED"),
    ERROR("ERROR"),
    HEARTBEAT_PING("PING"),
    HEARTBEAT_PONG("PONG"),
    SESSION_HEALTH_PONG("WS_PONG");

    private final String type;

    WebSocketMessageType(String type) {
        this.type = type;
    }

    @JsonValue
    public String type() {
        return type;
    }

    public boolean isSameType(String rawType) {
        return type.equalsIgnoreCase(rawType);
    }

    public static Optional<WebSocketMessageType> from(String rawType) {
        return Arrays.stream(values())
                     .filter(value -> value.isSameType(rawType))
                     .findFirst();
    }
}
