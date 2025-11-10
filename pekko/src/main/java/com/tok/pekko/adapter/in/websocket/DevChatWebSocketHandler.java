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
        try {
            ActorRef<ClientSessionCommand> existing = managementService.findClientSession(context.userId());

            return CompletableFuture.completedFuture(existing);
        } catch (ClientSessionActorManagementService.ClientSessionNotFoundException ignored) {
            return managementService.createClientSessionActor(messageSender, context.userId());
        }
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

        forwardMessageToActor(context, payload);
        return Mono.empty();
    }

    private boolean handlePingMessage(
            String payload,
            Sinks.Many<WebSocketMessageSender.WebSocketPayload> sink
    ) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText();

            if ("PING".equalsIgnoreCase(type)) {
                sink.tryEmitNext(new WebSocketMessageSender.WebSocketPayload("PONG", null));
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
            String type = node.path("type").asText();

            if ("WS_PONG".equalsIgnoreCase(type)) {
                clientSession.thenAccept(actorRef -> actorRef.tell(new SessionPongReceived()));
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private void forwardMessageToActor(WebSocketConnectionContext context, String messagePayload) {
        EntityRef<ChannelEntityCommand> channelEntity = getEntity(context.channelId());
        SendMessage command = new SendMessage(
                context.userId(),
                messagePayload
        );

        channelEntity.tell(command);
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
