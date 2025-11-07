package com.tok.pekko.domain.channel.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelRoleTest {

    @Test
    void 역할_이름으로_특정_역할을_찾을_수_있다() {
        // when
        ChannelRole ownerRole = ChannelRole.find("OWNER");
        ChannelRole managerRole = ChannelRole.find("MANAGER");
        ChannelRole memberRole = ChannelRole.find("MEMBER");

        // then
        assertAll(
                () -> assertThat(ownerRole).isEqualTo(ChannelRole.OWNER),
                () -> assertThat(managerRole).isEqualTo(ChannelRole.MANAGER),
                () -> assertThat(memberRole).isEqualTo(ChannelRole.MEMBER)
        );
    }

    @Test
    void 존재하지_않는_역할_이름으로는_역할을_찾을_수_없다() {
        // when & then
        assertThatThrownBy(() -> ChannelRole.find("INVALID_ROLE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 역할 입니다.");
    }

    @Test
    void 오너_역할인지_확인한다() {
        // when & then
        assertAll(
                () -> assertThat(ChannelRole.OWNER.isOwner()).isTrue(),
                () -> assertThat(ChannelRole.MANAGER.isOwner()).isFalse(),
                () -> assertThat(ChannelRole.MEMBER.isOwner()).isFalse()
        );
    }

    @Test
    void 매니저_역할인지_확인한다() {
        // when & then
        assertAll(
                () -> assertThat(ChannelRole.MANAGER.isManager()).isTrue(),
                () -> assertThat(ChannelRole.OWNER.isManager()).isFalse(),
                () -> assertThat(ChannelRole.MEMBER.isManager()).isFalse()
        );
    }

    @Test
    void 멤버_역할인지_확인한다() {
        // when & then
        assertAll(
                () -> assertThat(ChannelRole.MEMBER.isMember()).isTrue(),
                () -> assertThat(ChannelRole.OWNER.isMember()).isFalse(),
                () -> assertThat(ChannelRole.MANAGER.isMember()).isFalse()
        );
    }
}
