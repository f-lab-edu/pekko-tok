package com.tok.pekko.domain.user.model;

import com.tok.pekko.domain.user.model.vo.UserId;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserTest {

    @Test
    void 소셜_로그인_정보로_User를_초기화할_수_있다() {
        // given
        RegistrationId registrationId = RegistrationId.KAKAO;
        String socialId = "kakao12345";

        // when
        User actual = User.create(registrationId, socialId);

        // then
        assertAll(
                () -> assertThat(actual.getId()).isEqualTo(UserId.EMPTY_USER_ID),
                () -> assertThat(actual.getRegistrationId()).isEqualTo(RegistrationId.KAKAO),
                () -> assertThat(actual.getSocialId()).isEqualTo(socialId)
        );
    }

    @Test
    void 모든_필드를_포함한_User를_초기화할_수_있다() {
        // given
        Long userId = 100L;
        RegistrationId registrationId = RegistrationId.KAKAO;
        String socialId = "kakao12345";

        // when
        User actual = User.create(userId, registrationId, socialId);

        // then
        assertAll(
                () -> assertThat(actual.getId()).isEqualTo(UserId.create(userId)),
                () -> assertThat(actual.getRegistrationId()).isEqualTo(RegistrationId.KAKAO),
                () -> assertThat(actual.getSocialId()).isEqualTo(socialId)
        );
    }

    @Test
    void 기존_User에_id를_할당할_수_있다() {
        // given
        RegistrationId registrationId = RegistrationId.KAKAO;
        String socialId = "kakao12345";
        User existingUser = User.create(registrationId, socialId);
        Long assignedId = 100L;

        // when
        User actual = existingUser.withAssignedId(assignedId);

        // then
        assertAll(
                () -> assertThat(actual.getId()).isEqualTo(UserId.create(assignedId)),
                () -> assertThat(actual.getRegistrationId()).isEqualTo(RegistrationId.KAKAO),
                () -> assertThat(actual.getSocialId()).isEqualTo(socialId)
        );
    }
}
