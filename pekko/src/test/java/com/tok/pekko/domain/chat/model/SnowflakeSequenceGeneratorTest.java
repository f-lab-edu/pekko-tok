package com.tok.pekko.domain.chat.model;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SnowflakeSequenceGeneratorTest {

    private static final long TEST_CHANNEL_ID = 42L;

    @Test
    void 유효한_채널_ID로_생성자를_호출하면_정상적으로_생성된다() {
        // when & then
        assertDoesNotThrow(() -> new SnowflakeSequenceGenerator(TEST_CHANNEL_ID));
    }

    @Test
    void 음수_채널_ID로는_생성할_수_없다() {
        // given
        long negativeChannelId = -1L;

        // when & then
        assertThatThrownBy(() -> new SnowflakeSequenceGenerator(negativeChannelId))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("채널 식별자는 양수여야 합니다");
    }

    @Test
    void 최대값을_초과하는_채널_ID는_10비트로_마스킹된다() {
        // given
        long exceedingChannelId = 2048L;

        // when
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(exceedingChannelId);
        long id = generator.nextSequence();
        long extractedChannelId = SnowflakeSequenceGenerator.getNodeIdFromId(id);

        // then
        assertThat(extractedChannelId).isLessThanOrEqualTo(1023L);
    }

    @Test
    void nextId를_호출하면_양수_ID가_생성된다() {
        // given
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID);

        // when
        long id = generator.nextSequence();

        // then
        assertThat(id).isPositive();
    }

    @Test
    void 연속으로_ID를_생성하면_증가하는_값을_반환한다() {
        // given
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID);

        // when
        long id1 = generator.nextSequence();
        long id2 = generator.nextSequence();

        // then
        assertThat(id2).isGreaterThan(id1);
    }

    @RepeatedTest(10)
    void 여러_번_ID를_생성해도_모든_ID는_유일하다() {
        // given
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID);
        Set<Long> ids = new HashSet<>();

        // when
        for (int i = 0; i < 1000; i++) {
            long id = generator.nextSequence();
            ids.add(id);
        }

        // then
        assertThat(ids).hasSize(1000);
    }

    @Test
    void 동일한_밀리초_내에서_ID를_생성하면_시퀀스가_순차적으로_증가한다() {
        // given
        Clock fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID, fixedClock);

        // when
        long id1 = generator.nextSequence();
        long id2 = generator.nextSequence();
        long id3 = generator.nextSequence();

        // then
        long sequence1 = SnowflakeSequenceGenerator.getSequenceFromId(id1);
        long sequence2 = SnowflakeSequenceGenerator.getSequenceFromId(id2);
        long sequence3 = SnowflakeSequenceGenerator.getSequenceFromId(id3);

        assertAll(
                () -> assertThat(sequence1).isZero(),
                () -> assertThat(sequence2).isEqualTo(1L),
                () -> assertThat(sequence3).isEqualTo(2L)
        );
    }

    @Test
    void 다른_밀리초에_ID를_생성하면_시퀀스가_0으로_초기화된다() {
        // given
        MutableClock clock = new MutableClock(Instant.now());
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID, clock);

        // when
        long id1 = generator.nextSequence();
        clock.advanceMillis(1);
        long id2 = generator.nextSequence();

        // then
        long sequence1 = SnowflakeSequenceGenerator.getSequenceFromId(id1);
        long sequence2 = SnowflakeSequenceGenerator.getSequenceFromId(id2);

        assertAll(
                () -> assertThat(sequence1).isZero(),
                () -> assertThat(sequence2).isZero()
        );
    }

    @Test
    void 시퀀스가_최대값에_도달하면_다음_밀리초까지_대기한다() {
        // given
        MutableClock clock = new MutableClock(Instant.now());
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID, clock);

        // when
        for (int i = 0; i < 4096; i++) {
            generator.nextSequence();
        }
        clock.advanceMillis(1);
        long nextId = generator.nextSequence();

        // then
        long sequence = SnowflakeSequenceGenerator.getSequenceFromId(nextId);
        assertThat(sequence).isZero();
    }

    @Test
    void 시계가_역행하면_IllegalStateException이_발생한다() {
        // given
        MutableClock clock = new MutableClock(Instant.now());
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID, clock);
        generator.nextSequence();

        // when
        clock.advanceMillis(-10);

        // then
        assertThatThrownBy(generator::nextSequence)
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("시계가")
                  .hasMessageContaining("역행했습니다");
    }

    @Test
    void 시계_역행_예외_메시지에는_역행한_시간이_포함된다() {
        // given
        MutableClock clock = new MutableClock(Instant.now());
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID, clock);
        generator.nextSequence();

        // when
        clock.advanceMillis(-100);

        // then
        assertThatThrownBy(generator::nextSequence)
                  .hasMessageContaining("100ms");
    }

    @Test
    void 생성된_ID로부터_타임스탬프를_추출할_수_있다() {
        // given
        Instant now = Instant.now();
        Clock fixedClock = Clock.fixed(now, ZoneId.systemDefault());
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID, fixedClock);

        // when
        long id = generator.nextSequence();
        long extractedTimestamp = SnowflakeSequenceGenerator.getTimestampFromId(id);

        // then
        assertThat(extractedTimestamp).isEqualTo(now.toEpochMilli());
    }

    @Test
    void 생성된_ID로부터_노드_ID를_추출할_수_있다() {
        // given
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID);

        // when
        long id = generator.nextSequence();
        long extractedChannelId = SnowflakeSequenceGenerator.getNodeIdFromId(id);

        // then
        assertThat(extractedChannelId).isEqualTo(TEST_CHANNEL_ID);
    }

    @Test
    void 생성된_ID로부터_시퀀스를_추출할_수_있다() {
        // given
        Clock fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID, fixedClock);

        // when
        generator.nextSequence();
        generator.nextSequence();
        long id = generator.nextSequence();
        long extractedSequence = SnowflakeSequenceGenerator.getSequenceFromId(id);

        // then
        assertThat(extractedSequence).isEqualTo(2L);
    }

    @Test
    void 생성된_ID의_모든_구성_요소를_올바르게_파싱할_수_있다() {
        // given
        long channelId = 123L;
        Instant now = Instant.parse("2025-10-19T07:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneId.systemDefault());
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(channelId, fixedClock);

        // when
        generator.nextSequence();
        generator.nextSequence();
        long id = generator.nextSequence();

        // then
        assertAll(
                () -> assertThat(SnowflakeSequenceGenerator.getTimestampFromId(id)).isEqualTo(now.toEpochMilli()),
                () -> assertThat(SnowflakeSequenceGenerator.getNodeIdFromId(id)).isEqualTo(channelId),
                () -> assertThat(SnowflakeSequenceGenerator.getSequenceFromId(id)).isEqualTo(2L)
        );
    }

    @Test
    void 시퀀스_최대값_4095까지_정상적으로_생성된다() {
        // given
        Clock fixedClock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        SnowflakeSequenceGenerator generator = new SnowflakeSequenceGenerator(TEST_CHANNEL_ID, fixedClock);

        // when
        long lastId = 0;
        for (int i = 0; i < 4096; i++) {
            lastId = generator.nextSequence();
        }

        // then
        long lastSequence = SnowflakeSequenceGenerator.getSequenceFromId(lastId);
        assertThat(lastSequence).isEqualTo(4095L);
    }

    static class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        public MutableClock(Instant instant) {
            this.instant = instant;
            this.zone = ZoneId.systemDefault();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        public void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }
    }
}
