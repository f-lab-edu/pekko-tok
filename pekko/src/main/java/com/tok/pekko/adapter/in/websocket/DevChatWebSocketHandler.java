package com.tok.pekko.adapter.in.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.adapter.out.websocket.WebSocketMessageSender;
import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SendMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.Shutdown;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
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

@Profile("dev")
@Component
@RequiredArgsConstructor
public class DevChatWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ClusterSharding clusterSharding;
    private final ClientSessionActorManagementService managementService;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        WebSocketConnectionContext context = extractConnectionContext(session);
        Sinks.Many<ChatMessage> messageSink = createMessageSink();

        CompletionStage<ActorRef<ClientSessionCommand>> clientSession = createClientSessionActor(context, messageSink);

        Flux<WebSocketMessage> inbound = handleInboundMessages(session, context);
        Flux<WebSocketMessage> outbound = handleOutboundMessages(session, messageSink);

        return session.send(outbound)
                      .and(inbound)
                      .doFinally(signal -> cleanupConnection(messageSink, clientSession));
    }

    private WebSocketConnectionContext extractConnectionContext(WebSocketSession session) {
        return WebSocketConnectionContext.from(session);
    }

    private Sinks.Many<ChatMessage> createMessageSink() {
        return Sinks.many()
                    .unicast()
                    .onBackpressureBuffer();
    }

    private CompletionStage<ActorRef<ClientSessionCommand>> createClientSessionActor(WebSocketConnectionContext context, Sinks.Many<ChatMessage> sink) {
        ClientMessageSender sender = new WebSocketMessageSender(sink);

        return managementService.createClientSessionActor(sender, context.userId());
    }

    private Flux<WebSocketMessage> handleInboundMessages(
            WebSocketSession session,
            WebSocketConnectionContext context
    ) {
        return session.receive()
                      .doOnNext(message -> forwardMessageToActor(context, message));
    }

    private void forwardMessageToActor(WebSocketConnectionContext context, WebSocketMessage message) {
        EntityRef<ChannelEntityCommand> entityRef = getEntityRef(context.channelId());
        SendMessage command = new SendMessage(
                context.userId(),
                message.getPayloadAsText()
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

    private void cleanupConnection(
            Sinks.Many<ChatMessage> sink,
            CompletionStage<ActorRef<ClientSessionCommand>> clientSession
    ) {
        clientSession.thenAccept(actorRef -> actorRef.tell(new Shutdown()));
        sink.tryEmitComplete();
    }

    private EntityRef<ChannelEntityCommand> getEntityRef(Long channelId) {
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
