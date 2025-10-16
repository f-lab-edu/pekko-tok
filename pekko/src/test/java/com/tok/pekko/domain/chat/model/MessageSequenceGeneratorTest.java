package com.tok.pekko.domain.chat.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MessageSequenceGeneratorTest {

    @Test
    void 이전_값에_1을_더한_값을_조회한다() {
        MessageSequenceGenerator sequenceGenerator = new MessageSequenceGenerator(0);

        long actual = sequenceGenerator.getNextSequence();

        assertThat(actual).isEqualTo(1L);
    }

    @Test
    void 연속적으로_호출하면_계속_1을_더한_값을_조회한다() {
        MessageSequenceGenerator sequenceGenerator = new MessageSequenceGenerator(0);

        assertThat(sequenceGenerator.getNextSequence()).isEqualTo(1L);
        assertThat(sequenceGenerator.getNextSequence()).isEqualTo(2L);
        assertThat(sequenceGenerator.getNextSequence()).isEqualTo(3L);
    }

    @ParameterizedTest(name = "초기 값이 {0}일 때 {1}을 반환한다")
    @CsvSource({
            "0, 1",
            "10, 11",
            "99, 100",
            "1000, 1001"
    })
    void 다양한_초기값에서_정상_작동한다(long initial, long expected) {
        MessageSequenceGenerator sequenceGenerator = new MessageSequenceGenerator(initial);

        long actual = sequenceGenerator.getNextSequence();

        assertThat(actual).isEqualTo(expected);
    }
}
