package com.tok.pekko.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.FoundRegisteredChannelIds;
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
        ParticipatingChannelRepository repository = Mockito.mock(ParticipatingChannelRepository.class);
        ChannelMembershipActorMessageAdapter adapter = new ChannelMembershipActorMessageAdapter(repository);
        Long userId = 1L;
        List<Long> channelIds = List.of(10L, 20L, 30L);

        given(repository.findAllChannelIds(userId)).willReturn(channelIds);

        // when
        adapter.sendParticipatingChannels(userId, replyProbe.ref());

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
        ParticipatingChannelRepository repository = Mockito.mock(ParticipatingChannelRepository.class);
        ChannelMembershipActorMessageAdapter adapter = new ChannelMembershipActorMessageAdapter(repository);
        Long userId = 1L;

        given(repository.findAllChannelIds(anyLong())).willThrow(new RuntimeException("Database error"));

        // when
        adapter.sendParticipatingChannels(userId, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 사용자가_참여한_채널이_없다면_아무_메시지도_전달하지_않는다() {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ParticipatingChannelRepository repository = Mockito.mock(ParticipatingChannelRepository.class);
        ChannelMembershipActorMessageAdapter adapter = new ChannelMembershipActorMessageAdapter(repository);
        Long userId = 1L;

        given(repository.findAllChannelIds(anyLong())).willReturn(null);

        // when
        adapter.sendParticipatingChannels(userId, replyProbe.ref());

        // then
        replyProbe.expectNoMessage(Duration.ofSeconds(1));
    }

    @Test
    void 사용자가_참여한_채널_ID_조회_시_호출자_스레드를_블로킹하지_않는다() throws InterruptedException {
        // given
        TestProbe<ClientSessionCommand> replyProbe = testKit.createTestProbe(ClientSessionCommand.class);
        ParticipatingChannelRepository repository = Mockito.mock(ParticipatingChannelRepository.class);
        ChannelMembershipActorMessageAdapter adapter = new ChannelMembershipActorMessageAdapter(repository);
        Long userId = 1L;
        List<Long> channelIds = List.of(10L, 20L);

        given(repository.findAllChannelIds(anyLong())).willReturn(channelIds);

        // when
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        Mono.fromRunnable(() -> adapter.sendParticipatingChannels(userId, replyProbe.ref()))
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
}

