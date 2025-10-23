package com.tok.pekko.adapter.in.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.adapter.out.websocket.WebSocketMessageSender;
import com.tok.pekko.application.actor.SessionActorManagementService;
import com.tok.pekko.domain.chat.model.ChatChannelEntity;
import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SendMessage;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ClusterSharding clusterSharding;
    private final SessionActorManagementService managementService;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        WebSocketConnectionContext context = extractConnectionContext(session);
        Sinks.Many<ChatMessage> messageSink = createMessageSink();

        registerSession(context, messageSink);

        Flux<WebSocketMessage> inbound = handleInboundMessages(session, context);
        Flux<WebSocketMessage> outbound = handleOutboundMessages(session, messageSink);

        return session.send(outbound)
                      .and(inbound)
                      .doFinally(signal -> cleanupConnection(context, messageSink));
    }

    private WebSocketConnectionContext extractConnectionContext(WebSocketSession session) {
        return WebSocketConnectionContext.from(session);
    }

    private Sinks.Many<ChatMessage> createMessageSink() {
        return Sinks.many()
                    .unicast()
                    .onBackpressureBuffer();
    }

    private void registerSession(WebSocketConnectionContext context, Sinks.Many<ChatMessage> sink) {
        ClientMessageSender sender = new WebSocketMessageSender(sink);
        managementService.registerSession(sender, context.userId(), context.channelId());
    }

    private Flux<WebSocketMessage> handleInboundMessages(
            WebSocketSession session,
            WebSocketConnectionContext context
    ) {
        return session.receive()
                      .doOnNext(message -> forwardMessageToActor(context, message));
    }

    private void forwardMessageToActor(WebSocketConnectionContext context, WebSocketMessage message) {
        EntityRef<ChatChannelEntityCommand> entityRef = getEntityRef(context.channelId());
        SendMessage command = new SendMessage(
                context.userId(),
                message.getPayloadAsText(),
                LocalDateTime.now(clock)
        );

        entityRef.tell(command);
    }

    private Flux<WebSocketMessage> handleOutboundMessages(
            WebSocketSession session,
            Sinks.Many<ChatMessage> sink
    ) {
        return sink.asFlux()
                   .flatMap(message -> serializeMessage(session, message));
    }

    private Mono<WebSocketMessage> serializeMessage(WebSocketSession session, ChatMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);

            return Mono.just(session.textMessage(json));
        } catch (JsonProcessingException ignored) {
            return Mono.empty();
        }
    }

    private void cleanupConnection(WebSocketConnectionContext context, Sinks.Many<ChatMessage> sink) {
        EntityRef<ChatChannelEntityCommand> entityRef = getEntityRef(context.channelId());

        managementService.terminateSession(context.channelId(), context.userId());
        entityRef.tell(new RemoveShutdownReader(context.userId()));
        sink.tryEmitComplete();
    }

    private EntityRef<ChatChannelEntityCommand> getEntityRef(Long channelId) {
        return clusterSharding.entityRefFor(
                ChatChannelEntity.ENTITY_TYPE_KEY,
                String.valueOf(channelId)
        );
    }

    private record WebSocketConnectionContext(Long channelId, Long userId) {

        static WebSocketConnectionContext from(WebSocketSession session) {
            URI uri = session.getHandshakeInfo().getUri();
            MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri)
                                                                       .build()
                                                                       .getQueryParams();

            Long channelId = extractRequiredParam(params, "channelId");
            Long userId = extractRequiredParam(params, "userId");

            return new WebSocketConnectionContext(channelId, userId);
        }

        private static Long extractRequiredParam(MultiValueMap<String, String> params, String key) {
            try {
                return Long.valueOf(Objects.requireNonNull(params.getFirst(key)));
            } catch (NullPointerException | NumberFormatException e) {
                throw new IllegalArgumentException(key + "에 해당하는 ID가 없거나 유효하지 않습니다.");
            }
        }
    }
}
