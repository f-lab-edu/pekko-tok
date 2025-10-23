package com.tok.pekko.adapter.in.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.application.actor.SessionActorManagementService;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.RemoveShutdownReader;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SendMessage;
import java.net.URI;
import java.time.Clock;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DevChatWebSocketHandlerTest {

    @Test
    void 정상적으로_웹소켓을_연결하면_필요한_흐름을_정의하고_Actor에게_메시지를_전달한다() throws Exception {
        // given
        Clock clock = Clock.systemDefaultZone();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        SessionActorManagementService managementService = mock(SessionActorManagementService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);

        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> entityRef = mock(EntityRef.class);

        DevChatWebSocketHandler handler = new DevChatWebSocketHandler(clock, objectMapper, clusterSharding, managementService);

        URI uri = new URI("ws://localhost:8080/ws/chat?channelId=1&userId=100");
        given(session.getHandshakeInfo()).willReturn(handshakeInfo);
        given(handshakeInfo.getUri()).willReturn(uri);
        given(session.receive()).willReturn(Flux.empty());
        given(session.send(any())).willReturn(Mono.empty());
        given(clusterSharding.<ChatChannelEntityCommand>entityRefFor(any(), anyString())).willReturn(entityRef);

        // when
        Mono<Void> result = handler.handle(session);

        // then
        StepVerifier.create(result)
                    .verifyComplete();

        verify(managementService).registerSession(any(ClientMessageSender.class), eq(100L), eq(1L));
        verify(managementService).terminateSession(1L, 100L);
        verify(entityRef).tell(any(RemoveShutdownReader.class));

    }

    @Test
    void 웹소켓으로_인한_메시지를_보낼_때_마다_EntityRe의_tell_메서드를_통해_적절한_메시지를_전달한다() throws Exception {
        // given
        Clock clock = Clock.systemDefaultZone();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        SessionActorManagementService managementService = mock(SessionActorManagementService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);
        WebSocketMessage webSocketMessage = mock(WebSocketMessage.class);

        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> entityRef = mock(EntityRef.class);

        DevChatWebSocketHandler handler = new DevChatWebSocketHandler(clock, objectMapper, clusterSharding, managementService);

        URI uri = new URI("ws://localhost:8080/ws/chat?channelId=1&userId=100");
        String messageContent = "테스트 메시지";

        given(session.getHandshakeInfo()).willReturn(handshakeInfo);
        given(handshakeInfo.getUri()).willReturn(uri);
        given(session.receive()).willReturn(Flux.just(webSocketMessage));
        given(webSocketMessage.getPayloadAsText()).willReturn(messageContent);
        given(session.send(any())).willReturn(Mono.empty());
        given(clusterSharding.<ChatChannelEntityCommand>entityRefFor(any(), anyString())).willReturn(entityRef);

        // when
        Mono<Void> result = handler.handle(session);

        // then
        StepVerifier.create(result)
                    .verifyComplete();

        verify(entityRef).tell(any(SendMessage.class));
    }

    @Test
    void 웹소켓_연결_시_channelId가_쿼리_파라미터에_없다면_예외가_발생한다() throws Exception {
        // given
        Clock clock = Clock.systemDefaultZone();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        SessionActorManagementService managementService = mock(SessionActorManagementService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);

        DevChatWebSocketHandler handler = new DevChatWebSocketHandler(clock, objectMapper, clusterSharding, managementService);

        URI uri = new URI("ws://localhost:8080/ws/chat?userId=100");
        given(session.getHandshakeInfo()).willReturn(handshakeInfo);
        given(handshakeInfo.getUri()).willReturn(uri);

        // when & Then
        assertThatThrownBy(() -> handler.handle(session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channelId");
    }

    @Test
    void 웹소켓_연결_시_userId가_쿼리_파라미터에_없다면_예외가_발생한다() throws Exception {
        // given
        Clock clock = Clock.systemDefaultZone();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        SessionActorManagementService managementService = mock(SessionActorManagementService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);

        DevChatWebSocketHandler handler = new DevChatWebSocketHandler(clock, objectMapper, clusterSharding, managementService);

        URI uri = new URI("ws://localhost:8080/ws/chat?channelId=1");
        given(session.getHandshakeInfo()).willReturn(handshakeInfo);
        given(handshakeInfo.getUri()).willReturn(uri);

        // when & then
        assertThatThrownBy(() -> handler.handle(session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void 메시지_직렬화에_실패하면_빈_Mono를_반환한다() throws Exception {
        // given
        Clock clock = Clock.systemDefaultZone();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        SessionActorManagementService managementService = mock(SessionActorManagementService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);

        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> entityRef = mock(EntityRef.class);

        DevChatWebSocketHandler handler = new DevChatWebSocketHandler(clock, objectMapper, clusterSharding, managementService);

        URI uri = new URI("ws://localhost:8080/ws/chat?channelId=1&userId=100");

        given(session.getHandshakeInfo()).willReturn(handshakeInfo);
        given(handshakeInfo.getUri()).willReturn(uri);
        given(session.receive()).willReturn(Flux.empty());
        given(session.send(any())).willReturn(Mono.empty());
        given(clusterSharding.<ChatChannelEntityCommand>entityRefFor(any(), anyString())).willReturn(entityRef);
        given(objectMapper.writeValueAsString(any())).willThrow(JsonProcessingException.class);

        // when
        Mono<Void> result = handler.handle(session);

        // then
        StepVerifier.create(result)
                    .verifyComplete();
    }

    @Test
    void 웹소켓_세션_종료_시_리소스_정리에_필요한_메서드를_호출한다() throws Exception {
        // given
        Clock clock = Clock.systemDefaultZone();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        SessionActorManagementService managementService = mock(SessionActorManagementService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);

        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> entityRef = mock(EntityRef.class);

        DevChatWebSocketHandler handler = new DevChatWebSocketHandler(clock, objectMapper, clusterSharding, managementService);

        URI uri = new URI("ws://localhost:8080/ws/chat?channelId=1&userId=100");

        given(session.getHandshakeInfo()).willReturn(handshakeInfo);
        given(handshakeInfo.getUri()).willReturn(uri);
        given(session.receive()).willReturn(Flux.empty());
        given(session.send(any())).willReturn(Mono.empty());
        given(clusterSharding.<ChatChannelEntityCommand>entityRefFor(any(), anyString())).willReturn(entityRef);

        // when
        Mono<Void> result = handler.handle(session);

        // then
        StepVerifier.create(result)
                    .verifyComplete();

        verify(managementService).terminateSession(1L, 100L);
        verify(entityRef).tell(any(RemoveShutdownReader.class));
    }

    @Test
    void 웹소켓_세션으로_여러_메시지를_받으면_순차적으로_처리한다() throws Exception {
        // given
        Clock clock = Clock.systemDefaultZone();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        SessionActorManagementService managementService = mock(SessionActorManagementService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);
        WebSocketMessage message1 = mock(WebSocketMessage.class);
        WebSocketMessage message2 = mock(WebSocketMessage.class);

        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> entityRef = mock(EntityRef.class);

        DevChatWebSocketHandler handler = new DevChatWebSocketHandler(clock, objectMapper, clusterSharding, managementService);

        URI uri = new URI("ws://localhost:8080/ws/chat?channelId=1&userId=100");

        given(session.getHandshakeInfo()).willReturn(handshakeInfo);
        given(handshakeInfo.getUri()).willReturn(uri);
        given(session.receive()).willReturn(Flux.just(message1, message2));
        given(message1.getPayloadAsText()).willReturn("메시지1");
        given(message2.getPayloadAsText()).willReturn("메시지2");
        given(session.send(any())).willReturn(Mono.empty());
        given(clusterSharding.<ChatChannelEntityCommand>entityRefFor(any(), anyString())).willReturn(entityRef);

        // when
        Mono<Void> result = handler.handle(session);

        // then
        StepVerifier.create(result)
                    .verifyComplete();

        verify(entityRef, times(2)).tell(any(SendMessage.class));
    }

    @Test
    void 웹소켓_연결_시_유효한_파라미터가_전달되면_파라미터를_기준으로_세션을_등록한다() throws Exception {
        // given
        Clock clock = Clock.systemDefaultZone();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        SessionActorManagementService managementService = mock(SessionActorManagementService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);

        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> entityRef = mock(EntityRef.class);

        DevChatWebSocketHandler handler = new DevChatWebSocketHandler(clock, objectMapper, clusterSharding, managementService);

        URI uri = new URI("ws://localhost:8080/ws/chat?channelId=123&userId=456");

        given(session.getHandshakeInfo()).willReturn(handshakeInfo);
        given(handshakeInfo.getUri()).willReturn(uri);
        given(session.receive()).willReturn(Flux.empty());
        given(session.send(any())).willReturn(Mono.empty());
        given(clusterSharding.<ChatChannelEntityCommand>entityRefFor(any(), anyString())).willReturn(entityRef);

        // when
        handler.handle(session).subscribe();

        // then
        verify(managementService).registerSession(any(ClientMessageSender.class), eq(456L), eq(123L));
    }

    @Test
    @DisplayName("EntityRef에 올바른 channelId로 접근")
    void 웹소켓_연결_시_유효한_파라미터가_전달되면_유효한_channelId로_ChatChannelEntity를_조회한다() throws Exception {
        // given
        Clock clock = Clock.systemDefaultZone();
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        SessionActorManagementService managementService = mock(SessionActorManagementService.class);
        WebSocketSession session = mock(WebSocketSession.class);
        HandshakeInfo handshakeInfo = mock(HandshakeInfo.class);

        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> entityRef = mock(EntityRef.class);

        DevChatWebSocketHandler handler = new DevChatWebSocketHandler(clock, objectMapper, clusterSharding, managementService);

        URI uri = new URI("ws://localhost:8080/ws/chat?channelId=999&userId=100");

        given(session.getHandshakeInfo()).willReturn(handshakeInfo);
        given(handshakeInfo.getUri()).willReturn(uri);
        given(session.receive()).willReturn(Flux.empty());
        given(session.send(any())).willReturn(Mono.empty());
        given(clusterSharding.<ChatChannelEntityCommand>entityRefFor(any(), anyString())).willReturn(entityRef);

        // when
        handler.handle(session).subscribe();

        // then
        verify(clusterSharding).entityRefFor(any(), eq("999"));
    }
}
