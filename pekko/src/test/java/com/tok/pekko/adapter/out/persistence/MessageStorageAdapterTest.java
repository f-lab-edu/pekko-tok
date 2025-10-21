package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.ChatChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.HistoryFound;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelProtocol.SyncRecentMessages;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.global.config.dev.BlockHoundTestInstallUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MessageStorageAdapterTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        BlockHoundTestInstallUtils.install();
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void store_성공_시_replyTo에게_SyncPersistedMessage_메시지를_전달한다() {
        // given
        TestProbe<ChatChannelEntityCommand> replyProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);
        ChatMessage message = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 17, 0, 0)
        );
        ChatMessage persistedMessage = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 17, 0, 0)
        );

        given(messageRepository.save(any(ChatMessage.class))).willReturn(persistedMessage);

        // when
        adapter.store(message, replyProbe.ref());

        // then
        SyncPersistedMessage syncPersistedMessage = replyProbe.expectMessageClass(
                SyncPersistedMessage.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(syncPersistedMessage.message()).isEqualTo(persistedMessage),
                () -> assertThat(syncPersistedMessage.message().channelId()).isEqualTo(1L),
                () -> assertThat(syncPersistedMessage.message().userId()).isEqualTo(100L),
                () -> assertThat(syncPersistedMessage.message().message()).isEqualTo("Test Message")
        );
    }

    @Test
    void store_실패_시_replyTo에게_메시지를_전달하지_않는다() {
        // given
        TestProbe<ChatChannelEntityCommand> replyProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);
        ChatMessage message = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 17, 0, 0)
        );

        given(messageRepository.save(any(ChatMessage.class))).willThrow(new RuntimeException("Database error"));

        // when
        adapter.store(message, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void store_null_반환_시_replyTo에게_메시지를_전달하지_않는다() {
        // given
        TestProbe<ChatChannelEntityCommand> replyProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);
        ChatMessage message = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 17, 0, 0)
        );

        given(messageRepository.save(any(ChatMessage.class))).willReturn(null);

        // when
        adapter.store(message, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void findHistory_성공_시_replyTo에게_HistoryFound_메시지를_전달한다() {
        // given
        TestProbe<ChatChannelEntityCommand> writerProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        TestProbe<ChatChannelReaderCommand> readerProbe = testKit.createTestProbe(ChatChannelReaderCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);

        Long channelId = 2L;
        long messageSequence = 10L;
        int size = 5;

        List<ChatMessage> historyMessages = List.of(
                ChatMessage.create(channelId, 100L, 1L, "Message 1", LocalDateTime.now()),
                ChatMessage.create(channelId, 101L, 2L, "Message 2", LocalDateTime.now())
        );

        given(messageRepository.findHistory(eq(channelId), eq(messageSequence), eq(size))).willReturn(historyMessages);

        // when
        adapter.findHistory(channelId, messageSequence, size, writerProbe.ref(), readerProbe.ref());

        // then
        HistoryFound historyFound = writerProbe.expectMessageClass(
                HistoryFound.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(historyFound.history()).isEqualTo(historyMessages),
                () -> assertThat(historyFound.history()).hasSize(2),
                () -> assertThat(historyFound.replyTo()).isEqualTo(readerProbe.ref())
        );
    }

    @Test
    void findHistory_실패_시_replyTo에게_메시지를_전달하지_않는다() {
        // given
        TestProbe<ChatChannelEntityCommand> writerProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        TestProbe<ChatChannelReaderCommand> readerProbe = testKit.createTestProbe(ChatChannelReaderCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);

        Long channelId = 2L;
        long messageSequence = 10L;
        int size = 5;

        given(messageRepository.findHistory(anyLong(), anyLong(), anyInt()))
                .willThrow(new RuntimeException("Database error"));

        // when
        adapter.findHistory(channelId, messageSequence, size, writerProbe.ref(), readerProbe.ref());

        // then
        writerProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void findHistory_null_반환_시_replyTo에게_메시지를_전달하지_않는다() {
        // given
        TestProbe<ChatChannelEntityCommand> writerProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        TestProbe<ChatChannelReaderCommand> readerProbe = testKit.createTestProbe(ChatChannelReaderCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);

        Long channelId = 2L;
        long messageSequence = 10L;
        int size = 5;

        given(messageRepository.findHistory(anyLong(), anyLong(), anyInt())).willReturn(null);

        // when
        adapter.findHistory(channelId, messageSequence, size, writerProbe.ref(), readerProbe.ref());

        // then
        writerProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void findRecentMessages_성공_시_replyTo에게_SyncRecentMessages_메시지를_전달한다() {
        // given
        TestProbe<ChatChannelEntityCommand> replyProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);

        Long channelId = 3L;
        int size = 50;

        List<ChatMessage> recentMessages = List.of(
                ChatMessage.create(channelId, 200L, 1L, "Recent 1", LocalDateTime.now()),
                ChatMessage.create(channelId, 201L, 2L, "Recent 2", LocalDateTime.now()),
                ChatMessage.create(channelId, 202L, 3L, "Recent 3", LocalDateTime.now())
        );

        given(messageRepository.findLatest(eq(channelId), eq(size))).willReturn(recentMessages);

        // when
        adapter.findRecentMessages(channelId, size, replyProbe.ref());

        // then
        SyncRecentMessages syncRecentMessages = replyProbe.expectMessageClass(
                SyncRecentMessages.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(syncRecentMessages.messages()).isEqualTo(recentMessages),
                () -> assertThat(syncRecentMessages.messages()).hasSize(3)
        );
    }

    @Test
    void findRecentMessages_실패_시_replyTo에게_메시지를_전달하지_않는다() {
        // given
        TestProbe<ChatChannelEntityCommand> replyProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);

        Long channelId = 3L;
        int size = 50;

        given(messageRepository.findLatest(anyLong(), anyInt())).willThrow(new RuntimeException("Database error"));

        // when
        adapter.findRecentMessages(channelId, size, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void findRecentMessages_null_반환_시_replyTo에게_메시지를_전달하지_않는다() {
        // given
        TestProbe<ChatChannelEntityCommand> replyProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);

        Long channelId = 3L;
        int size = 50;

        given(messageRepository.findLatest(anyLong(), anyInt())).willReturn(null);

        // when
        adapter.findRecentMessages(channelId, size, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void store_호출_시_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ChatChannelEntityCommand> replyProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);
        ChatMessage message = ChatMessage.create(1L, 100L, 1L, "Test", LocalDateTime.now());

        given(messageRepository.save(any())).willReturn(message);

        // when
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        Mono.fromRunnable(() -> adapter.store(message, replyProbe.ref()))
            .subscribeOn(Schedulers.parallel())
            .doFinally(ignored -> latch.countDown())
            .subscribe(null, errorHolder::set);

        latch.await();

        // then
        assertAll(
                () -> assertThat(errorHolder.get()).isNull(),
                () -> replyProbe.expectMessageClass(SyncPersistedMessage.class, Duration.ofSeconds(1))
        );
    }

    @Test
    void findHistory_호출_시_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ChatChannelEntityCommand> writerProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        TestProbe<ChatChannelReaderCommand> readerProbe = testKit.createTestProbe(ChatChannelReaderCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);
        List<ChatMessage> historyMessages = List.of(
                ChatMessage.create(1L, 100L, 1L, "Message", LocalDateTime.now())
        );

        given(messageRepository.findHistory(anyLong(), anyLong(), anyInt())).willReturn(historyMessages);

        // when
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        Mono.fromRunnable(() -> adapter.findHistory(1L, 10L, 5, writerProbe.ref(), readerProbe.ref()))
            .subscribeOn(Schedulers.parallel())
            .doFinally(ignored -> latch.countDown())
            .subscribe(null, errorHolder::set);

        latch.await();

        // then
        assertAll(
                () -> assertThat(errorHolder.get()).isNull(),
                () -> writerProbe.expectMessageClass(HistoryFound.class, Duration.ofSeconds(1))
        );
    }

    @Test
    void findRecentMessages_호출_시_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ChatChannelEntityCommand> replyProbe = testKit.createTestProbe(ChatChannelEntityCommand.class);
        MessageRepository messageRepository = Mockito.mock(MessageRepository.class);
        MessageStorageAdapter adapter = new MessageStorageAdapter(messageRepository);
        List<ChatMessage> recentMessages = List.of(
                ChatMessage.create(1L, 100L, 1L, "Recent", LocalDateTime.now())
        );

        given(messageRepository.findLatest(anyLong(), anyInt())).willReturn(recentMessages);

        // when
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        Mono.fromRunnable(() -> adapter.findRecentMessages(1L, 50, replyProbe.ref()))
            .subscribeOn(Schedulers.parallel())
            .doFinally(ignored -> latch.countDown())
            .subscribe(null, errorHolder::set);

        latch.await();

        // then
        assertAll(
                () -> assertThat(errorHolder.get()).isNull(),
                () -> replyProbe.expectMessageClass(SyncRecentMessages.class, Duration.ofSeconds(1))
        );
    }
}
