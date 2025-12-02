package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncDeletedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncPersistedMessage;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.SyncRecentMessages;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundHistory;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

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
    void 메시지_저장_성공_시_동기화_이벤트를_전달한다() {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        ChatMessage message = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 17, 0, 0),
                LocalDateTime.of(2025, 10, 17, 17, 0, 0)
        );
        ChatMessage persistedMessage = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 17, 0, 0),
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
    void 메시지_저장_실패_시_이벤트를_전달하지_않는다() {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        ChatMessage message = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 17, 0, 0),
                LocalDateTime.of(2025, 10, 17, 17, 0, 0)
        );

        given(messageRepository.save(any(ChatMessage.class))).willThrow(new RuntimeException("Database error"));

        // when
        adapter.store(message, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 메시지_저장_결과가_null이면_이벤트를_전달하지_않는다() {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        ChatMessage message = ChatMessage.create(
                1L,
                100L,
                1L,
                "Test Message",
                LocalDateTime.of(2025, 10, 17, 17, 0, 0),
                LocalDateTime.of(2025, 10, 17, 17, 0, 0)
        );

        given(messageRepository.save(any(ChatMessage.class))).willReturn(null);

        // when
        adapter.store(message, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 히스토리_조회_성공_시_조회_결과를_전달한다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);

        Long channelId = 2L;
        long messageSequence = 10L;
        int size = 5;

        List<ChatMessage> historyMessages = List.of(
                ChatMessage.create(channelId, 100L, 1L, "Message 1", LocalDateTime.now(), LocalDateTime.now()),
                ChatMessage.create(channelId, 101L, 2L, "Message 2", LocalDateTime.now(), LocalDateTime.now())
        );

        given(messageRepository.findHistory(eq(channelId), eq(messageSequence), eq(size))).willReturn(historyMessages);

        // when
        adapter.findHistory(channelId, messageSequence, size, replyProbe.ref());

        // then
        FoundHistory foundHistory = replyProbe.expectMessageClass(
                FoundHistory.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(foundHistory.history()).isEqualTo(historyMessages),
                () -> assertThat(foundHistory.history()).hasSize(2)
        );
    }

    @Test
    void 히스토리_조회_실패_시_결과를_전달하지_않는다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);

        Long channelId = 2L;
        long messageSequence = 10L;
        int size = 5;

        given(messageRepository.findHistory(anyLong(), anyLong(), anyInt()))
                .willThrow(new RuntimeException("Database error"));

        // when
        adapter.findHistory(channelId, messageSequence, size, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }


    @Test
    void 히스토리_조회_결과가_null이면_결과를_전달하지_않는다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);

        Long channelId = 2L;
        long messageSequence = 10L;
        int size = 5;

        given(messageRepository.findHistory(anyLong(), anyLong(), anyInt())).willReturn(null);

        // when
        adapter.findHistory(channelId, messageSequence, size, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 최근_메시지_조회_성공_시_조회_결과를_전달한다() {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);

        Long channelId = 3L;
        int size = 50;

        List<ChatMessage> recentMessages = List.of(
                ChatMessage.create(channelId, 200L, 1L, "Recent 1", LocalDateTime.now(), LocalDateTime.now()),
                ChatMessage.create(channelId, 201L, 2L, "Recent 2", LocalDateTime.now(), LocalDateTime.now()),
                ChatMessage.create(channelId, 202L, 3L, "Recent 3", LocalDateTime.now(), LocalDateTime.now())
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
    void 최근_메시지_조회_실패_시_결과를_전달하지_않는다() {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);

        Long channelId = 3L;
        int size = 50;

        given(messageRepository.findLatest(anyLong(), anyInt())).willThrow(new RuntimeException("Database error"));

        // when
        adapter.findRecentMessages(channelId, size, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 최근_메시지_조회_결과가_null이면_결과를_전달하지_않는다() {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);

        Long channelId = 3L;
        int size = 50;

        given(messageRepository.findLatest(anyLong(), anyInt())).willReturn(null);

        // when
        adapter.findRecentMessages(channelId, size, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 메시지_저장_시_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        ChatMessage message = ChatMessage.create(1L, 100L, 1L, "Test", LocalDateTime.now(), LocalDateTime.now());

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
    void 히스토리_조회_시_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        List<ChatMessage> historyMessages = List.of(
                ChatMessage.create(1L, 100L, 1L, "Message", LocalDateTime.now(), LocalDateTime.now())
        );

        given(messageRepository.findHistory(anyLong(), anyLong(), anyInt())).willReturn(historyMessages);

        // when
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        Mono.fromRunnable(() -> adapter.findHistory(1L, 10L, 5, replyProbe.ref()))
            .subscribeOn(Schedulers.parallel())
            .doFinally(ignored -> latch.countDown())
            .subscribe(null, errorHolder::set);

        latch.await();

        // then
        assertAll(
                () -> assertThat(errorHolder.get()).isNull(),
                () -> replyProbe.expectMessageClass(FoundHistory.class, Duration.ofSeconds(1))
        );
    }

    @Test
    void 최근_메시지_조회_시_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        List<ChatMessage> recentMessages = List.of(
                ChatMessage.create(1L, 100L, 1L, "Recent", LocalDateTime.now(), LocalDateTime.now())
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

    @Test
    void 메시지_삭제_성공_시_삭제_이벤트를_전달한다() {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        Long messageId = 1L;

        willDoNothing().given(messageRepository).delete(eq(messageId));

        // when
        adapter.delete(messageId, replyProbe.ref());

        // then
        SyncDeletedMessage syncDeletedMessage = replyProbe.expectMessageClass(
                SyncDeletedMessage.class,
                Duration.ofSeconds(3)
        );
        assertThat(syncDeletedMessage.messageId()).isEqualTo(messageId);
    }

    @Test
    void 메시지_삭제_실패_시_이벤트를_전달하지_않는다() {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        Long messageId = 1L;

        willThrow(new RuntimeException("Database error")).given(messageRepository).delete(anyLong());

        // when
        adapter.delete(messageId, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 메시지_삭제_시_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        Long messageId = 1L;

        willDoNothing().given(messageRepository).delete(anyLong());

        // when
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        Mono.fromRunnable(() -> adapter.delete(messageId, replyProbe.ref()))
            .subscribeOn(Schedulers.parallel())
            .doFinally(ignored -> latch.countDown())
            .subscribe(null, errorHolder::set);

        latch.await();

        // then
        assertAll(
                () -> assertThat(errorHolder.get()).isNull(),
                () -> replyProbe.expectMessageClass(SyncDeletedMessage.class, Duration.ofSeconds(1))
        );
    }

    @Test
    void 메시지_수정_성공_시_Repository의_update를_호출한다() {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        Long messageId = 1L;
        String updatedMessage = "Updated Message";

        willDoNothing().given(messageRepository).update(eq(messageId), eq(updatedMessage));

        // when
        adapter.update(messageId, updatedMessage, replyProbe.ref());

        // then
        verify(messageRepository, timeout(1000)).update(messageId, updatedMessage);
    }

    @Test
    void 메시지_수정_실패_시_이벤트를_전달하지_않는다() {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        Long messageId = 1L;
        String updatedMessage = "Updated Message";

        willThrow(new RuntimeException("Database error")).given(messageRepository).update(anyLong(), any());

        // when
        adapter.update(messageId, updatedMessage, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 메시지_수정_시_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ChannelEntityCommand> replyProbe = testKit.createTestProbe(ChannelEntityCommand.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        Scheduler scheduler = Schedulers.immediate();
        MessageStorageAdapter adapter = new MessageStorageAdapter(scheduler, messageRepository);
        Long messageId = 1L;
        String updatedMessage = "Updated Message";

        willDoNothing().given(messageRepository).update(anyLong(), any());

        // when
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        Mono.fromRunnable(() -> adapter.update(messageId, updatedMessage, replyProbe.ref()))
            .subscribeOn(Schedulers.parallel())
            .doFinally(ignored -> latch.countDown())
            .subscribe(null, errorHolder::set);

        latch.await();

        // then
        assertThat(errorHolder.get()).isNull();
    }
}
