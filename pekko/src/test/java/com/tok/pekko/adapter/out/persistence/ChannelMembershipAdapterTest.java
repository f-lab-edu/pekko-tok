package com.tok.pekko.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;

import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundRegisteredChannelIds;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncJoinChannel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.SyncLeaveChannel;
import com.tok.pekko.global.config.dev.BlockHoundTestInstallUtils;
import java.time.Duration;
import java.util.List;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelMembershipAdapterTest {

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
    void 사용자가_참여한_채널_ID를_조회해_FoundRegisterChannelIds_메시지로_전달한다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ChannelMembershipRepository repository = Mockito.mock(ChannelMembershipRepository.class);
        ChannelMembershipAdapter adapter = new ChannelMembershipAdapter(repository);
        Long userId = 1L;
        List<Long> channelIds = List.of(10L, 20L, 30L);

        given(repository.findAllIChannelIds(userId)).willReturn(channelIds);

        // when
        adapter.findParticipatingChannels(userId, replyProbe.ref());

        // then
        FoundRegisteredChannelIds actual = replyProbe.expectMessageClass(
                FoundRegisteredChannelIds.class,
                Duration.ofSeconds(3)
        );
        assertAll(
                () -> assertThat(actual.channelIds()).isEqualTo(channelIds),
                () -> assertThat(actual.channelIds()).hasSize(3)
        );
    }

    @Test
    void 사용자가_참여한_채널_ID를_조회하지_못했다면_아무_메시지도_전달하지_않는다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ChannelMembershipRepository repository = Mockito.mock(ChannelMembershipRepository.class);
        ChannelMembershipAdapter adapter = new ChannelMembershipAdapter(repository);
        Long userId = 1L;

        given(repository.findAllIChannelIds(anyLong())).willThrow(new RuntimeException("Database error"));

        // when
        adapter.findParticipatingChannels(userId, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 사용자가_참여한_채널이_없다면_아무_메시지도_전달하지_않는다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ChannelMembershipRepository repository = Mockito.mock(ChannelMembershipRepository.class);
        ChannelMembershipAdapter adapter = new ChannelMembershipAdapter(repository);
        Long userId = 1L;

        given(repository.findAllIChannelIds(anyLong())).willReturn(null);

        // when
        adapter.findParticipatingChannels(userId, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 사용자가_새롭게_채널에_참여하면_SyncJoinChannel_메시지를_전달한다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ChannelMembershipRepository repository = Mockito.mock(ChannelMembershipRepository.class);
        ChannelMembershipAdapter adapter = new ChannelMembershipAdapter(repository);
        Long userId = 1L;
        Long channelId = 10L;

        willDoNothing().given(repository).save(eq(userId), eq(channelId));

        // when
        adapter.joinChannel(userId, channelId, replyProbe.ref());

        // then
        SyncJoinChannel actual = replyProbe.expectMessageClass(
                SyncJoinChannel.class,
                Duration.ofSeconds(3)
        );
        assertThat(actual.channelId()).isEqualTo(channelId);
    }

    @Test
    void 사용자가_새롭게_채널에_참여하지_못했다면_아무_메시지도_전달하지_않는다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ChannelMembershipRepository repository = Mockito.mock(ChannelMembershipRepository.class);
        ChannelMembershipAdapter adapter = new ChannelMembershipAdapter(repository);
        Long userId = 1L;
        Long channelId = 10L;

        willThrow(new RuntimeException("Database error")).given(repository).save(anyLong(), anyLong());

        // when
        adapter.joinChannel(userId, channelId, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 사용자가_채널을_탈퇴하면_SyncLeaveChannel_메시지를_전달한다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ChannelMembershipRepository repository = Mockito.mock(ChannelMembershipRepository.class);
        ChannelMembershipAdapter adapter = new ChannelMembershipAdapter(repository);
        Long userId = 1L;
        Long channelId = 10L;

        willDoNothing().given(repository).save(eq(userId), eq(channelId));

        // when
        adapter.leaveChannel(userId, channelId, replyProbe.ref());

        // then
        SyncLeaveChannel syncLeaveChannel = replyProbe.expectMessageClass(
                SyncLeaveChannel.class,
                Duration.ofSeconds(3)
        );
        assertThat(syncLeaveChannel.channelId()).isEqualTo(channelId);
    }

    @Test
    void 사용자가_채널을_탈퇴하지_못하면_아무_메시지도_전달하지_않는다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ChannelMembershipRepository repository = Mockito.mock(ChannelMembershipRepository.class);
        ChannelMembershipAdapter adapter = new ChannelMembershipAdapter(repository);
        Long userId = 1L;
        Long channelId = 10L;

        willThrow(new RuntimeException("Database error")).given(repository).save(anyLong(), anyLong());

        // when
        adapter.leaveChannel(userId, channelId, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 사용자가_참여한_채널_ID_조회_시_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ChannelMembershipRepository repository = Mockito.mock(ChannelMembershipRepository.class);
        ChannelMembershipAdapter adapter = new ChannelMembershipAdapter(repository);
        Long userId = 1L;
        List<Long> channelIds = List.of(10L, 20L);

        given(repository.findAllIChannelIds(anyLong())).willReturn(channelIds);

        // when
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        Mono.fromRunnable(() -> adapter.findParticipatingChannels(userId, replyProbe.ref()))
            .subscribeOn(Schedulers.parallel())
            .doFinally(ignored -> latch.countDown())
            .subscribe(null, errorHolder::set);

        latch.await();

        // then
        assertAll(
                () -> assertThat(errorHolder.get()).isNull(),
                () -> replyProbe.expectMessageClass(FoundRegisteredChannelIds.class, Duration.ofSeconds(1))
        );
    }

    @Test
    void 사용자가_새롭게_채널에_참여할_때_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ChannelMembershipRepository repository = Mockito.mock(ChannelMembershipRepository.class);
        ChannelMembershipAdapter adapter = new ChannelMembershipAdapter(repository);
        Long userId = 1L;
        Long channelId = 10L;

        willDoNothing().given(repository).save(anyLong(), anyLong());

        // when
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        Mono.fromRunnable(() -> adapter.joinChannel(userId, channelId, replyProbe.ref()))
            .subscribeOn(Schedulers.parallel())
            .doFinally(ignored -> latch.countDown())
            .subscribe(null, errorHolder::set);

        latch.await();

        // then
        assertAll(
                () -> assertThat(errorHolder.get()).isNull(),
                () -> replyProbe.expectMessageClass(SyncJoinChannel.class, Duration.ofSeconds(1))
        );
    }

    @Test
    void 사용자가_채널을_탈퇴할_때_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ChannelMembershipRepository repository = Mockito.mock(ChannelMembershipRepository.class);
        ChannelMembershipAdapter adapter = new ChannelMembershipAdapter(repository);
        Long userId = 1L;
        Long channelId = 10L;

        willDoNothing().given(repository).save(anyLong(), anyLong());

        // when
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        Mono.fromRunnable(() -> adapter.leaveChannel(userId, channelId, replyProbe.ref()))
            .subscribeOn(Schedulers.parallel())
            .doFinally(ignored -> latch.countDown())
            .subscribe(null, errorHolder::set);

        latch.await();

        // then
        assertAll(
                () -> assertThat(errorHolder.get()).isNull(),
                () -> replyProbe.expectMessageClass(SyncLeaveChannel.class, Duration.ofSeconds(1))
        );
    }
}

