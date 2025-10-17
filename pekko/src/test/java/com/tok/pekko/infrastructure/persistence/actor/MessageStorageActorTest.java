package com.tok.pekko.infrastructure.persistence.actor;

import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncRecentMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.HistoryLoaded;
import com.tok.pekko.infrastructure.persistence.actor.MessageStorageActor.FetchHistory;
import com.tok.pekko.infrastructure.persistence.actor.MessageStorageActor.FetchRecentMessages;
import com.tok.pekko.infrastructure.persistence.actor.MessageStorageActor.MessageStoreCommand;
import com.tok.pekko.infrastructure.persistence.actor.MessageStorageActor.Store;
import com.tok.pekko.infrastructure.persistence.repository.MessageRepository;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MessageStorageActorTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        Config config = ConfigFactory.load();

        testKit = ActorTestKit.create(config);
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void Store_메시지를_받으면_MessageRepository에_메시지_저장을_요청한다() {
        // given
        MessageRepository mockMessageRepository = mock(MessageRepository.class);
        ActorRef<MessageStoreCommand> messageStorageActor = testKit.spawn(
                MessageStorageActor.create(mockMessageRepository)
        );

        ChatMessage message = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 15, 0, 0)
        );

        // when
        messageStorageActor.tell(new Store(message));

        // then
        verify(mockMessageRepository, timeout(1000)).save(message);
    }

    @Test
    void FetchHistory_메시지를_받으면_MessageRepository에서_조회하고_replyTo에_HistoryLoaded_메시지를_전송한다() {
        // given
        MessageRepository mockMessageRepository = mock(MessageRepository.class);
        ActorRef<MessageStoreCommand> messageStorageActor = testKit.spawn(
                MessageStorageActor.create(mockMessageRepository)
        );

        TestProbe<ChatChannelReaderCommand> replyProbe = testKit.createTestProbe(ChatChannelReaderCommand.class);

        Long channelId = 1L;
        long messageSequence = 10L;
        int size = 5;

        List<ChatMessage> historyMessages = Arrays.asList(
                new ChatMessage(1L, 5L, 100L, 5L, "Message 5", LocalDateTime.of(2025, 10, 17, 15, 0, 0)),
                new ChatMessage(1L, 6L, 101L, 6L, "Message 6", LocalDateTime.of(2025, 10, 17, 15, 0, 1)),
                new ChatMessage(1L, 7L, 102L, 7L, "Message 7", LocalDateTime.of(2025, 10, 17, 15, 0, 2))
        );

        given(mockMessageRepository.findHistory(channelId, messageSequence, size)).willReturn(historyMessages);

        // when
        messageStorageActor.tell(new FetchHistory(channelId, messageSequence, size, replyProbe.ref()));

        // then
        verify(mockMessageRepository, timeout(1000)).findHistory(channelId, messageSequence, size);

        HistoryLoaded historyLoaded = replyProbe.expectMessageClass(
                HistoryLoaded.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(historyLoaded.history()).hasSize(3),
                () -> assertThat(historyLoaded.history()).isEqualTo(historyMessages)
        );
    }

    @Test
    void FetchHistory_메시지를_받았을_때_조회_결과가_비어있으면_빈_리스트를_replyTo에_HistoryLoaded_메시지로_전송한다() {
        // given
        MessageRepository mockMessageRepository = mock(MessageRepository.class);
        ActorRef<MessageStoreCommand> messageStorageActor = testKit.spawn(
                MessageStorageActor.create(mockMessageRepository)
        );

        TestProbe<ChatChannelReaderCommand> replyProbe = testKit.createTestProbe(ChatChannelReaderCommand.class);

        Long channelId = 999L;
        long messageSequence = 100L;
        int size = 10;

        given(mockMessageRepository.findHistory(channelId, messageSequence, size)).willReturn(List.of());

        // when
        messageStorageActor.tell(new FetchHistory(channelId, messageSequence, size, replyProbe.ref()));

        // then
        verify(mockMessageRepository, timeout(1000)).findHistory(channelId, messageSequence, size);

        HistoryLoaded historyLoaded = replyProbe.expectMessageClass(
                HistoryLoaded.class,
                Duration.ofSeconds(3)
        );
        assertThat(historyLoaded.history()).isEmpty();
    }

    @Test
    void FetchRecentMessages_메시지를_받으면_MessageRepository에서_조회하고_replyTo에_SyncRecentMessages_메시지를_전송한다() {
        // given
        MessageRepository mockMessageRepository = mock(MessageRepository.class);
        ActorRef<MessageStoreCommand> messageStorageActor = testKit.spawn(
                MessageStorageActor.create(mockMessageRepository)
        );

        TestProbe<ChatChannelEntityCommand> replyProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);

        Long channelId = 1L;
        int size = 10;

        List<ChatMessage> recentMessages = Arrays.asList(
                new ChatMessage(1L, 10L, 100L, 10L, "Message 10", LocalDateTime.of(2025, 10, 17, 16, 0, 0)),
                new ChatMessage(1L, 9L, 101L, 9L, "Message 9", LocalDateTime.of(2025, 10, 17, 15, 59, 0)),
                new ChatMessage(1L, 8L, 102L, 8L, "Message 8", LocalDateTime.of(2025, 10, 17, 15, 58, 0))
        );

        given(mockMessageRepository.findLatest(channelId, size)).willReturn(recentMessages);

        // when
        messageStorageActor.tell(new FetchRecentMessages(channelId, size, replyProbe.ref()));

        // then
        verify(mockMessageRepository, timeout(1000)).findLatest(channelId, size);

        SyncRecentMessages syncRecentMessages = replyProbe.expectMessageClass(
                SyncRecentMessages.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(syncRecentMessages.messages()).hasSize(3),
                () -> assertThat(syncRecentMessages.messages()).isEqualTo(recentMessages)
        );
    }

    @Test
    void 여러_Store_메시지를_연속으로_받으면_모두_MessageRepository에_저장한다() {
        // given
        MessageRepository mockMessageRepository = mock(MessageRepository.class);
        ActorRef<MessageStoreCommand> messageStorageActor = testKit.spawn(
                MessageStorageActor.create(mockMessageRepository)
        );

        ChatMessage message1 = ChatMessage.create(4L, 400L, 1L, "Message 1", LocalDateTime.of(2025, 10, 17, 17, 0, 0));
        ChatMessage message2 = ChatMessage.create(4L, 401L, 2L, "Message 2", LocalDateTime.of(2025, 10, 17, 17, 0, 1));
        ChatMessage message3 = ChatMessage.create(4L, 402L, 3L, "Message 3", LocalDateTime.of(2025, 10, 17, 17, 0, 2));

        // when
        messageStorageActor.tell(new Store(message1));
        messageStorageActor.tell(new Store(message2));
        messageStorageActor.tell(new Store(message3));

        // then
        assertAll(
                () -> verify(mockMessageRepository, timeout(1000)).save(message1),
                () -> verify(mockMessageRepository, timeout(1000)).save(message2),
                () -> verify(mockMessageRepository, timeout(1000)).save(message3)
        );
    }

    @Test
    void 여러_FetchHistory_메시지를_연속으로_받으면_각각_replyTo에_HistoryLoaded_메시지를_전송한다() {
        // given
        MessageRepository mockMessageRepository = mock(MessageRepository.class);
        ActorRef<MessageStoreCommand> messageStorageActor = testKit.spawn(
                MessageStorageActor.create(mockMessageRepository)
        );

        TestProbe<ChatChannelReaderCommand> reply1Probe = testKit.createTestProbe(ChatChannelReaderCommand.class);
        TestProbe<ChatChannelReaderCommand> reply2Probe = testKit.createTestProbe(ChatChannelReaderCommand.class);

        List<ChatMessage> history1 = List.of(
                new ChatMessage(1L, 10L, 100L, 10L, "History 10", LocalDateTime.now())
        );
        List<ChatMessage> history2 = List.of(
                new ChatMessage(1L, 20L, 200L, 20L, "History 20", LocalDateTime.now())
        );

        given(mockMessageRepository.findHistory(eq(1L), eq(10L), eq(5))).willReturn(history1);
        given(mockMessageRepository.findHistory(eq(2L), eq(20L), eq(5))).willReturn(history2);

        // when
        messageStorageActor.tell(new FetchHistory(1L, 10L, 5, reply1Probe.ref()));
        messageStorageActor.tell(new FetchHistory(2L, 20L, 5, reply2Probe.ref()));

        // then
        HistoryLoaded historyLoaded1 = reply1Probe.expectMessageClass(
                HistoryLoaded.class,
                Duration.ofSeconds(3)
        );
        assertThat(historyLoaded1.history()).isEqualTo(history1);

        HistoryLoaded historyLoaded2 = reply2Probe.expectMessageClass(
                HistoryLoaded.class,
                Duration.ofSeconds(3)
        );
        assertThat(historyLoaded2.history()).isEqualTo(history2);
    }
}
