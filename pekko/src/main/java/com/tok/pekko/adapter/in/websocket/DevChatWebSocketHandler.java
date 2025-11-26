package com.tok.pekko.adapter.in.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tok.pekko.adapter.out.websocket.WebSocketMessageSender;
import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SendMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ReSyncSession;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SessionPongReceived;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.context.annotation.Profile;
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
@Profile("dev")
@Component
@RequiredArgsConstructor
public class DevChatWebSocketHandler implements WebSocketHandler {

    private static final String HEARTBEAT_PING_MESSAGE_TYPE = "PING";
    private static final String HEARTBEAT_PONG_MESSAGE_TYPE = "PONG";
    private static final String SESSION_HEALTH_PONG_MESSAGE_TYPE = "WS_PONG";
    private static final String MESSAGE_SCHEMA_TYPE = "type";

    private final ObjectMapper objectMapper;
    private final ClusterSharding clusterSharding;
    private final ClientSessionActorManagementService managementService;
    private final Map<Long, WebSocketMessageSender> clientMessageSenders = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        WebSocketConnectionContext context = extractConnectionContext(session);
        Sinks.Many<WebSocketMessageSender.WebSocketPayload> messageSink = createMessageSink();
        WebSocketMessageSender messageSender = getOrCreateMessageSender(context, messageSink);

        CompletionStage<ActorRef<ClientSessionCommand>> clientSession =
                getOrCreateClientSessionActor(context, messageSender);

        Mono<Void> inbound = handleInboundMessages(session, context, messageSink, clientSession);
        Flux<WebSocketMessage> outbound = handleOutboundMessages(session, messageSink);

        return session.send(outbound)
                      .and(inbound)
                      .doFinally(signal -> cleanupConnection(messageSender, messageSink));
    }

    private WebSocketConnectionContext extractConnectionContext(WebSocketSession session) {
        return WebSocketConnectionContext.from(session);
    }

    private Sinks.Many<WebSocketMessageSender.WebSocketPayload> createMessageSink() {
        return Sinks.many()
                    .unicast()
                    .onBackpressureBuffer();
    }

    private WebSocketMessageSender getOrCreateMessageSender(
            WebSocketConnectionContext context,
            Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink
    ) {
        return clientMessageSenders.compute(
                context.userId(),
                (userId, sender) -> {
                    WebSocketMessageSender actualSender = sender != null ? sender : new WebSocketMessageSender();
                    actualSender.attachSink(sink);
                    return actualSender;
                }
        );
    }

    private CompletionStage<ActorRef<ClientSessionCommand>> getOrCreateClientSessionActor(
            WebSocketConnectionContext context,
            WebSocketMessageSender messageSender
    ) {
        return managementService.findClientSessionOptional(context.userId())
                                .map(this::reSyncAndWrap)
                                .orElseGet(() -> managementService.createClientSessionActor(messageSender, context.userId()));
    }

    private CompletionStage<ActorRef<ClientSessionCommand>> reSyncAndWrap(ActorRef<ClientSessionCommand> actorRef) {
        actorRef.tell(new ReSyncSession());
        return CompletableFuture.completedFuture(actorRef);
    }

    private Mono<Void> handleInboundMessages(
            WebSocketSession session,
            WebSocketConnectionContext context,
            Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink,
            CompletionStage<ActorRef<ClientSessionCommand>> clientSession
    ) {
        return session.receive()
                      .flatMap(message -> processInboundMessage(context, sink, clientSession, message))
                      .then();
    }

    private Mono<Void> processInboundMessage(
            WebSocketConnectionContext context,
            Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink,
            CompletionStage<ActorRef<ClientSessionCommand>> clientSession,
            WebSocketMessage message
    ) {
        String payload = message.getPayloadAsText();

        if (handleClientHealthPong(payload, clientSession)) {
            return Mono.empty();
        }
        if (handlePingMessage(payload, sink)) {
            return Mono.empty();
        }

        forwardMessageToActor(payload, context.userId());
        return Mono.empty();
    }

    private boolean handlePingMessage(
            String payload,
            Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink
    ) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText();

            if (HEARTBEAT_PING_MESSAGE_TYPE.equalsIgnoreCase(type)) {
                sink.tryEmitNext(new WebSocketMessageSender.WebSocketPayload(HEARTBEAT_PONG_MESSAGE_TYPE, null));
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private boolean handleClientHealthPong(
            String payload,
            CompletionStage<ActorRef<ClientSessionCommand>> clientSession
    ) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path(MESSAGE_SCHEMA_TYPE).asText();

            if (SESSION_HEALTH_PONG_MESSAGE_TYPE.equalsIgnoreCase(type)) {
                clientSession.thenAccept(actorRef -> actorRef.tell(new SessionPongReceived()));
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private void forwardMessageToActor(String messagePayload, Long userId) {
        ClientMessage clientMessage = parseClientMessage(messagePayload);

        if (clientMessage == null) {
            return;
        }

        EntityRef<ChannelEntityCommand> channelEntity = getEntity(clientMessage.channelId());

        channelEntity.tell(new SendMessage(userId, clientMessage.message()));
    }

    private Flux<WebSocketMessage> handleOutboundMessages(
            WebSocketSession session,
            Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink
    ) {
        return sink.asFlux()
                   .flatMap(payload -> serializeMessage(session, payload));
    }

    private Mono<WebSocketMessage> serializeMessage(
            WebSocketSession session,
            WebSocketMessageSender.WebSocketPayload payload
    ) {
        try {
            String json = objectMapper.writeValueAsString(payload);

            return Mono.just(session.textMessage(json));
        } catch (JsonProcessingException ignored) {
            return Mono.empty();
        }
    }

    private void cleanupConnection(
            WebSocketMessageSender messageSender,
            Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink
    ) {
        messageSender.detachSink(sink);
        sink.tryEmitComplete();
    }

    private EntityRef<ChannelEntityCommand> getEntity(Long channelId) {
        return clusterSharding.entityRefFor(
                ChannelEntity.ENTITY_TYPE_KEY,
                String.valueOf(channelId)
        );
    }

    private ClientMessage parseClientMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            Long channelId = node.path("channelId").asLong();
            String message = node.path("message").asText();

            if (channelId <= 0 || message == null || message.isBlank()) {
                return null;
            }

            return new ClientMessage(channelId, message);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record ClientMessage(Long channelId, String message) { }

    private record WebSocketConnectionContext(Long userId) {

        static WebSocketConnectionContext from(WebSocketSession session) {
            URI uri = session.getHandshakeInfo().getUri();
            MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri)
                                                                       .build()
                                                                       .getQueryParams();

            Long userId = extractRequiredParam(params, "userId");

            return new WebSocketConnectionContext(userId);
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
