package com.tok.pekko.domain.user.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserIdTest {

    @Test
    void 양수_값을_가진_ID로_초기화한다() {
        // when
        UserId userId = UserId.create(1L);

        // then
        assertAll(
                () -> assertThat(userId).isNotNull(),
                () -> assertThat(userId.getValue()).isEqualTo(1L)
        );
    }

    @ParameterizedTest(name = "{0}일 때 초기화에 실패한다")
    @NullSource
    @ValueSource(longs = {0, -1})
    void 유효하지_않은_ID로는_초기화할_수_없다(Long id) {
        // when & then
        assertThatThrownBy(() -> UserId.create(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 양수여야 합니다.");
    }

    @Test
    void 같은_값의_ID를_가진_UserId는_동등하다() {
        // given
        UserId userId1 = UserId.create(1L);
        UserId userId2 = UserId.create(1L);

        // when & then
        assertAll(
                () -> assertThat(userId1).isEqualTo(userId2),
                () -> assertThat(userId1).hasSameHashCodeAs(userId2)
        );
    }

    @Test
    void 다른_값의_ID를_가진_UserId는_동등하지_않다() {
        // given
        UserId userId1 = UserId.create(1L);
        UserId userId2 = UserId.create(2L);

        // when & then
        assertAll(
                () -> assertThat(userId1).isNotEqualTo(userId2),
                () -> assertThat(userId1).doesNotHaveSameHashCodeAs(userId2)
        );
    }

    @Test
    void 동일한_ID인지_확인한다() {
        // given
        UserId userId = UserId.create(1L);

        // when & then
        assertAll(
                () -> assertThat(userId.isEqualId(1L)).isTrue(),
                () -> assertThat(userId.isEqualId(2L)).isFalse()
        );
    }
}
