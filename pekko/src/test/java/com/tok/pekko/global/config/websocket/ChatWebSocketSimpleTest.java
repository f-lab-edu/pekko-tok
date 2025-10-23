package com.tok.pekko.global.config.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tok.pekko.adapter.in.websocket.DevChatWebSocketHandler;
import com.tok.pekko.adapter.out.websocket.ClientMessageSender;
import com.tok.pekko.application.actor.SessionActorManagementService;
import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SendMessage;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatWebSocketSimpleTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private ClusterSharding clusterSharding;

    @MockitoBean
    private SessionActorManagementService sessionService;

    private WebSocketClient client;
    private String baseUrl;
    private EntityRef<ChatChannelEntityCommand> entityRef;

    @BeforeEach
    void setUp() {
        reset(clusterSharding, sessionService);

        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> mockEntityRef = mock(EntityRef.class);
        entityRef = mockEntityRef;

        given(clusterSharding.<ChatChannelEntityCommand>entityRefFor(any(), anyString()))
                .willReturn(entityRef);

        client = new ReactorNettyWebSocketClient();
        baseUrl = "ws://localhost:" + port;
    }

    @Test
    void 웹소켓_연결에_성공한다() {
        // given
        URI uri = URI.create(baseUrl + "/ws/chat?channelId=1&userId=100");

        // when
        client.execute(uri, session ->
                Mono.delay(Duration.ofMillis(10)).then()
        ).block(Duration.ofMillis(100));

        // then
        verify(sessionService).registerSession(any(), anyLong(), anyLong());
    }

    @Test
    void 메시지_전송_시_EntityRef로_SendMessage_메시지를_전달한다() {
        // given
        URI uri = URI.create(baseUrl + "/ws/chat?channelId=1&userId=100");
        ArgumentCaptor<ChatChannelEntityCommand> captor =
                ArgumentCaptor.forClass(ChatChannelEntityCommand.class);

        // when
        client.execute(uri, session ->
                session.send(Mono.just(session.textMessage("테스트 메시지")))
                       .then(Mono.delay(Duration.ofMillis(10)))
                       .then()
        ).block(Duration.ofMillis(100));

        // then
        verify(entityRef, timeout(200).atLeastOnce()).tell(captor.capture());

        List<SendMessage> sendMessages = captor.getAllValues().stream()
                                               .filter(SendMessage.class::isInstance)
                                               .map(SendMessage.class::cast)
                                               .toList();

        assertThat(sendMessages).hasSize(1);
        SendMessage sendMessage = sendMessages.get(0);
        assertThat(sendMessage.userId()).isEqualTo(100L);
        assertThat(sendMessage.message()).isEqualTo("테스트 메시지");
    }

    @Test
    void 서버에서_클라이언트로_메시지를_전송한다() throws Exception {
        // given
        URI uri = URI.create(baseUrl + "/ws/chat?channelId=1&userId=100");
        CountDownLatch receiveLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        ArgumentCaptor<ClientMessageSender> senderCaptor =
                ArgumentCaptor.forClass(ClientMessageSender.class);

        // when
        Disposable connection = client.execute(uri, session ->
                session.receive()
                       .map(WebSocketMessage::getPayloadAsText)
                       .doOnNext(msg -> {
                           receivedMessage.set(msg);
                           receiveLatch.countDown();
                       })
                       .then()
        ).subscribe();

        then(sessionService).should(timeout(200)).registerSession(senderCaptor.capture(), anyLong(), anyLong());

        ClientMessageSender sender = senderCaptor.getValue();
        ChatMessage testMessage = ChatMessage.create(
                1L, 100L, 1L, "서버 메시지", LocalDateTime.now()
        );
        sender.sendMessage(testMessage);

        // then
        assertThat(receiveLatch.await(200, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(receivedMessage.get()).contains("서버 메시지");

        connection.dispose();
    }

    @Test
    void 웹소켓_연결_종료_시_리소스_정리_메서드를_호출한다() {
        // given
        URI uri = URI.create(baseUrl + "/ws/chat?channelId=1&userId=100");

        // when
        client.execute(uri, session ->
                Mono.delay(Duration.ofMillis(10)).then()
        ).block(Duration.ofMillis(100));

        // then
        verify(sessionService, timeout(200)).registerSession(any(), anyLong(), anyLong());
        verify(sessionService, timeout(200)).terminateSession(1L, 100L);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public DevChatWebSocketHandler devChatWebSocketHandler(
                Clock clock,
                ObjectMapper objectMapper,
                ClusterSharding clusterSharding,
                SessionActorManagementService managementService
        ) {
            return new DevChatWebSocketHandler(clock, objectMapper, clusterSharding, managementService);
        }
    }
}
