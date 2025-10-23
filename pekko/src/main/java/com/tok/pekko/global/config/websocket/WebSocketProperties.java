package com.tok.pekko.global.config.websocket;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.websocket")
public record WebSocketProperties(String endpoint, int handlerOrder, Cors cors) {

    public record Cors(
            boolean allowCredentials,
            List<String> allowedOriginPatterns,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            long maxAge
    ) {}
}
