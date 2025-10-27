package com.tok.pekko.domain.chat.model;

import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.RequestHistory;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncDeletion;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.SyncNewCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverDeletedMessage;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.DeliverHistory;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChatChannelReaderActorTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void SyncNewCommand_메시지를_받으면_채팅_메시지를_ChatMessages에_추가하고_ClientSessionActor에_전달한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(mockMessages, channelEntity, clientSessionProbe.ref())
        );

        ChatMessage newMessage = ChatMessage.create(
                1L,
                1001L,
                1L,
                "Hello World",
                LocalDateTime.now()
        );

        // when
        readerActor.tell(new SyncNewCommand(newMessage));

        // then
        verify(mockMessages, timeout(1000)).add(newMessage);

        DeliverCommand deliveredCommand = clientSessionProbe.expectMessageClass(
                DeliverCommand.class,
                Duration.ofSeconds(3)
        );
        assertThat(deliveredCommand.message()).isEqualTo(newMessage);
    }

    @Test
    void RequestHistory_메시지를_받으면_관리하고_있는_채팅_메시지를_조회해_ClientSessionActor에_전달한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(mockMessages, channelEntity, clientSessionProbe.ref())
        );

        long messageSequence = 100L;
        int size = 10;
        List<ChatMessage> historyMessages = List.of(
                new ChatMessage(1L, 90L, 2001L, 90L, "Message 90", LocalDateTime.now()),
                new ChatMessage(1L, 91L, 2002L, 91L, "Message 91", LocalDateTime.now()),
                new ChatMessage(1L, 92L, 2001L, 92L, "Message 92", LocalDateTime.now())
        );

        given(mockMessages.getHistory(messageSequence, size)).willReturn(historyMessages);

        // when
        readerActor.tell(new RequestHistory(messageSequence, size));

        // then
        verify(mockMessages, timeout(1000)).getHistory(messageSequence, size);

        DeliverHistory deliveredHistory = clientSessionProbe.expectMessageClass(
                DeliverHistory.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(deliveredHistory.messages()).hasSize(3),
                () -> assertThat(deliveredHistory.messages()).isEqualTo(historyMessages)
        );
    }

    @Test
    void RequestHistory_메시지를_받았을_때_관리하고_있는_채팅_메시지가_비어있으면_빈_리스트를_ClientSession에_전달한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(mockMessages, channelEntity, clientSessionProbe.ref())
        );

        long messageSequence = 10L;
        int size = 5;
        given(mockMessages.getHistory(messageSequence, size)).willReturn(List.of());

        // when
        readerActor.tell(new RequestHistory(messageSequence, size));

        // then
        verify(mockMessages, timeout(1000)).getHistory(messageSequence, size);

        DeliverHistory deliveredHistory = clientSessionProbe.expectMessageClass(
                DeliverHistory.class,
                Duration.ofSeconds(3)
        );
        assertThat(deliveredHistory.messages()).isEmpty();
    }

    @Test
    void Shutdown_메시지를_받으면_ClientSessionActor에_Shutdown_메시지를_전달하고_ChatChannelReaderActor가_종료된다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(mockMessages, channelEntity, clientSessionProbe.ref())
        );

        // when
        readerActor.tell(new Shutdown());

        // then
        clientSessionProbe.expectMessageClass(
                ClientSessionProtocol.Shutdown.class,
                Duration.ofSeconds(3)
        );

        TestProbe<Void> terminationProbe = testKit.createTestProbe();
        terminationProbe.expectTerminated(readerActor, Duration.ofSeconds(3));
    }

    @Test
    void SyncDeletion_메시지를_받으면_ChatMessages에서_메시지를_삭제하고_ClientSessionActor에_전달한다() {
        // given
        ChatMessages mockMessages = mock(ChatMessages.class);
        @SuppressWarnings("unchecked")
        EntityRef<ChatChannelEntityCommand> channelEntity = mock(EntityRef.class);
        TestProbe<ClientSessionCommand> clientSessionProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ActorRef<ChatChannelReaderCommand> readerActor = testKit.spawn(
                ChatChannelReaderActor.create(mockMessages, channelEntity, clientSessionProbe.ref())
        );

        Long messageId = 1L;
        ChatMessage deletedMessage = new ChatMessage(
                messageId,
                1L,
                1001L,
                1L,
                "Deleted Message",
                LocalDateTime.now()
        );

        given(mockMessages.delete(messageId)).willReturn(deletedMessage);

        // when
        readerActor.tell(new SyncDeletion(messageId));

        // then
        verify(mockMessages, timeout(1000)).delete(messageId);

        DeliverDeletedMessage deliveredDeletedMessage = clientSessionProbe.expectMessageClass(
                DeliverDeletedMessage.class,
                Duration.ofSeconds(3)
        );
        assertThat(deliveredDeletedMessage.deletedMessage()).isEqualTo(deletedMessage);
    }

}
