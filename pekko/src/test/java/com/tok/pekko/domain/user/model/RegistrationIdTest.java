package com.tok.pekko.domain.user.model;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RegistrationIdTest {

    @Test
    void 소셜_서비스_이름으로_RegistrationId를_찾을_수_있다() {
        // when
        RegistrationId actual = RegistrationId.findBy("kakao");

        // then
        assertThat(actual).isEqualTo(RegistrationId.KAKAO);
    }

    @Test
    void 유효하지_않은_이름으로_조회할_수_없다() {
        // when & then
        assertThatThrownBy(() -> RegistrationId.findBy("invalid_service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 소셜 로그인 서비스입니다.");
    }
}
