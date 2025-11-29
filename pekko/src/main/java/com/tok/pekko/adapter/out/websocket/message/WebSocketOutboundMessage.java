package com.tok.pekko.adapter.out.websocket.message;

import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.WebSocketEmptyPayload;
import com.tok.pekko.adapter.out.websocket.WebSocketMessageType;

public record WebSocketOutboundMessage(WebSocketMessageType type, WebSocketMessagePayload payload) {

    public static WebSocketOutboundMessage PING_MESSAGE = new WebSocketOutboundMessage(
            WebSocketMessageType.WS_HEALTH_PING,
            WebSocketEmptyPayload.INSTANCE
    );
    public static WebSocketOutboundMessage RECONNECT_MESSAGE = new WebSocketOutboundMessage(
            WebSocketMessageType.WS_RECONNECT,
            WebSocketEmptyPayload.INSTANCE
    );

    public static WebSocketOutboundMessage withoutPayload(WebSocketMessageType type) {
        return new WebSocketOutboundMessage(type, WebSocketEmptyPayload.INSTANCE);
    }
}
