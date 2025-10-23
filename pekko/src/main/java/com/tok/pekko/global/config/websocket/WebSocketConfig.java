package com.tok.pekko.global.config.websocket;

import com.tok.pekko.adapter.in.websocket.DevChatWebSocketHandler;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebFluxConfigurer {

    private final WebSocketProperties properties;
    private final DevChatWebSocketHandler devChatWebSocketHandler;

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put(properties.endpoint(), devChatWebSocketHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(properties.handlerOrder());
        mapping.setCorsConfigurations(
                Map.of(properties.endpoint(), createCorsConfiguration())
        );

        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    private CorsConfiguration createCorsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        WebSocketProperties.Cors cors = properties.cors();

        config.setAllowCredentials(cors.allowCredentials());
        cors.allowedOriginPatterns().forEach(config::addAllowedOriginPattern);
        cors.allowedMethods().forEach(config::addAllowedMethod);
        cors.allowedHeaders().forEach(config::addAllowedHeader);
        config.setMaxAge(cors.maxAge());

        return config;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        WebSocketProperties.Cors cors = properties.cors();

        registry.addMapping("/**")
                .allowedOriginPatterns(cors.allowedOriginPatterns().toArray(String[]::new))
                .allowedMethods(cors.allowedMethods().toArray(String[]::new))
                .allowedHeaders(cors.allowedHeaders().toArray(String[]::new))
                .allowCredentials(cors.allowCredentials())
                .maxAge(cors.maxAge());
    }
}
