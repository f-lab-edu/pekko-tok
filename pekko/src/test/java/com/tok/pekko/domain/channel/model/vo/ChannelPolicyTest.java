package com.tok.pekko.domain.channel.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelPolicyTest {

    @Test
    void 채널_정책을_초기화한다() {
        // when
        ChannelPolicy policy = new ChannelPolicy(true, false, false);

        // then
        assertAll(
                () -> assertThat(policy).isNotNull(),
                () -> assertThat(policy.canEditOwnMessage()).isTrue(),
                () -> assertThat(policy.canDeleteOwnMessage()).isFalse(),
                () -> assertThat(policy.isPublic()).isFalse()
        );
    }

    @Test
    void 기본_채널_정책을_초기화한다() {
        // when
        ChannelPolicy policy = ChannelPolicy.defaultPolicy();

        // then
        assertAll(
                () -> assertThat(policy.canEditOwnMessage()).isTrue(),
                () -> assertThat(policy.canDeleteOwnMessage()).isTrue(),
                () -> assertThat(policy.isPublic()).isTrue()
        );
    }

    @Test
    void 자신이_작성한_메시지_수정_가능_여부를_변경한다() {
        // given
        ChannelPolicy policy = new ChannelPolicy(false, true, true);

        // when
        ChannelPolicy updatedPolicy = policy.updateEditOwnMessage(true);

        // then
        assertAll(
                () -> assertThat(updatedPolicy.canEditOwnMessage()).isTrue(),
                () -> assertThat(updatedPolicy.canDeleteOwnMessage()).isTrue(),
                () -> assertThat(updatedPolicy.isPublic()).isTrue()
        );
    }

    @Test
    void 자신이_작성한_메시지_삭제_가능_여부를_변경한다() {
        // given
        ChannelPolicy policy = new ChannelPolicy(true, false, false);

        // when
        ChannelPolicy updatedPolicy = policy.updateDeleteOwnMessage(true);

        // then
        assertAll(
                () -> assertThat(updatedPolicy.canEditOwnMessage()).isTrue(),
                () -> assertThat(updatedPolicy.canDeleteOwnMessage()).isTrue(),
                () -> assertThat(updatedPolicy.isPublic()).isFalse()
        );
    }

    @Test
    void 채널_공개_여부를_변경한다() {
        // given
        ChannelPolicy policy = new ChannelPolicy(true, true, false);

        // when
        ChannelPolicy updatedPolicy = policy.updatePublic(true);

        // then
        assertAll(
                () -> assertThat(updatedPolicy.canEditOwnMessage()).isTrue(),
                () -> assertThat(updatedPolicy.canDeleteOwnMessage()).isTrue(),
                () -> assertThat(updatedPolicy.isPublic()).isTrue()
        );
    }

    @Test
    void 같은_값을_가진_채널_정책은_동등하다() {
        // given
        ChannelPolicy policy1 = new ChannelPolicy(true, false, true);
        ChannelPolicy policy2 = new ChannelPolicy(true, false, true);

        // when & then
        assertAll(
                () -> assertThat(policy1).isEqualTo(policy2),
                () -> assertThat(policy1).hasSameHashCodeAs(policy2)
        );
    }

    @Test
    void 값이_다른_채널_정책은_동등하지_않다() {
        // given
        ChannelPolicy policy1 = new ChannelPolicy(true, true, true);
        ChannelPolicy policy2 = new ChannelPolicy(false, false, false);

        // when & then
        assertAll(
                () -> assertThat(policy1).isNotEqualTo(policy2),
                () -> assertThat(policy1).doesNotHaveSameHashCodeAs(policy2)
        );
    }

    @Test
    void 비공개_채널인지_확인한다() {
        // given
        ChannelPolicy policy = new ChannelPolicy(true, true, false);

        // when & then
        assertAll(
                () -> assertThat(policy.isPrivate()).isTrue(),
                () -> assertThat(policy.isPublic()).isFalse()
        );
    }

    @Test
    void 자신이_작성한_채팅_메시지_수정_불가_정책인지_확인한다() {
        // given
        ChannelPolicy policy = new ChannelPolicy(false, true, true);

        // when & then
        assertAll(
                () -> assertThat(policy.cannotEditOwnMessage()).isTrue(),
                () -> assertThat(policy.canEditOwnMessage()).isFalse()
        );
    }

    @Test
    void 자신이_작성한_채팅_메시지_삭제_불가_정책인지_확인한다() {
        // given
        ChannelPolicy policy = new ChannelPolicy(true, false, true);

        // when & then
        assertAll(
                () -> assertThat(policy.cannotDeleteOwnMessage()).isTrue(),
                () -> assertThat(policy.canDeleteOwnMessage()).isFalse()
        );
    }
}
