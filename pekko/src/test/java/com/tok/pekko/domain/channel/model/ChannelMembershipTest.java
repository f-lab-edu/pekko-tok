package com.tok.pekko.domain.channel.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelMembershipId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelMembershipTest {

    @Test
    void 유효한_값으로_채널_참여자를_초기화할_수_있다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        // when
        ChannelMembership membership = ChannelMembership.create(channelId, userId, role, joinedAt);

        // then
        assertAll(
                () -> assertThat(membership).isNotNull(),
                () -> assertThat(membership.getId()).isEqualTo(ChannelMembershipId.EMPTY_CHANNEL_MEMBERSHIP_ID),
                () -> assertThat(membership.getChannelId()).isEqualTo(channelId),
                () -> assertThat(membership.getUserId()).isEqualTo(userId),
                () -> assertThat(membership.getRole()).isEqualTo(role),
                () -> assertThat(membership.getJoinedAt()).isEqualTo(joinedAt)
        );
    }

    @Test
    void null_채널_ID로는_채널_참여자를_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ChannelMembership.create(null, UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채널 ID는 필수입니다.");
    }

    @Test
    void null_사용자_ID로는_채널_참여자를_초기화할_수_없다() {
        // given
        ChannelId channelId = ChannelId.create(10L);

        // when & then
        assertThatThrownBy(() -> ChannelMembership.create(channelId, null, ChannelRole.MEMBER, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 필수입니다.");
    }

    @Test
    void 비어있는_사용자_ID로는_채널_참여자를_초기화할_수_없다() {
        // given
        ChannelId channelId = ChannelId.create(10L);

        // when & then
        assertThatThrownBy(() -> ChannelMembership.create(channelId, UserId.EMPTY_USER_ID, ChannelRole.MEMBER, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 필수입니다.");
    }

    @Test
    void 멤버_역할로_초기화하면_아무_권한도_가지지_못한다() {
        // given
        ChannelId channelId = ChannelId.create(10L);

        // when
        ChannelMembership membership = ChannelMembership.create(channelId, UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());

        // then
        assertThat(membership.getPermissions().isEmpty()).isTrue();
    }

    @Test
    void 매니저에게_명시적_권한을_지정해_초기화할_수_있다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT, ChannelPermissionType.MEMBER_KICK);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);

        // when
        ChannelMembership membership = ChannelMembership.createManager(channelId, userId, permissions, joinedAt);

        // then
        assertAll(
                () -> assertThat(membership.getRole()).isEqualTo(ChannelRole.MANAGER),
                () -> assertThat(membership.getPermissions().has(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(membership.getPermissions().has(ChannelPermissionType.MEMBER_KICK)).isTrue()
        );
    }

    @Test
    void 영속화된_채널_참여자를_초기화할_수_있다() {
        // given
        Long memberId = 100L;
        Long userIdVal = 1L;
        Long channelIdVal = 10L;
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT, ChannelPermissionType.MEMBER_KICK);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);

        // when
        ChannelMembership membership = ChannelMembership.create(memberId, channelIdVal, userIdVal, role, permissions, joinedAt);

        // then
        assertAll(
                () -> assertThat(membership.getId()).isEqualTo(ChannelMembershipId.create(memberId)),
                () -> assertThat(membership.getChannelId()).isEqualTo(ChannelId.create(channelIdVal)),
                () -> assertThat(membership.getUserId()).isEqualTo(UserId.create(userIdVal)),
                () -> assertThat(membership.getRole()).isEqualTo(role),
                () -> assertThat(membership.getPermissions().has(ChannelPermissionType.MESSAGE_EDIT)).isTrue()
        );
    }

    @Test
    void 같은_채널_참여자_ID를_가진_참여자는_동등하다() {
        // given
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership1 = ChannelMembership.create(1L, 10L, userId.getValue(), ChannelRole.MEMBER,
                ChannelManagePermissions.ofMember(), joinedAt);
        ChannelMembership membership2 = ChannelMembership.create(1L, 10L, userId.getValue(), ChannelRole.OWNER,
                ChannelManagePermissions.ofOwner(), joinedAt);

        // when & then
        assertAll(
                () -> assertThat(membership1).isEqualTo(membership2),
                () -> assertThat(membership1).hasSameHashCodeAs(membership2)
        );
    }

    @Test
    void 다른_채널_참여자_ID를_가진_참여자는_동등하지_않다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership1 = ChannelMembership.create(1L, 10L, userId.getValue(), role,
                ChannelManagePermissions.ofMember(), joinedAt);
        ChannelMembership membership2 = ChannelMembership.create(2L, 10L, userId.getValue(), role,
                ChannelManagePermissions.ofMember(), joinedAt);

        // when & then
        assertAll(
                () -> assertThat(membership1).isNotEqualTo(membership2),
                () -> assertThat(membership1).doesNotHaveSameHashCodeAs(membership2)
        );
    }

    @Test
    void 채널_참여자에게_ID를_할당할_수_있다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        ChannelMembership membership = ChannelMembership.create(channelId, userId, role, joinedAt);
        Long assignedId = 100L;

        // when
        ChannelMembership actual = membership.withAssignedId(assignedId);

        // then
        assertAll(
                () ->assertThat(actual.getId()).isEqualTo(ChannelMembershipId.create(assignedId)),
                () ->assertThat(actual.getChannelId()).isEqualTo(channelId),
                () ->assertThat(actual.getUserId()).isEqualTo(userId),
                () ->assertThat(actual.getRole()).isEqualTo(role),
                () ->assertThat(actual.getJoinedAt()).isEqualTo(joinedAt)
        );
    }

    @Test
    void 매니저에게는_권한을_업데이트할_수_있다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(channelId, userId, role, joinedAt);
        Set<ChannelPermissionType> newPerms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT, ChannelPermissionType.MEMBER_KICK);
        ChannelManagePermissions newPermissions = ChannelManagePermissions.ofManager(newPerms);

        // when
        ChannelMembership updatedMembership = membership.updatePermissions(newPermissions);

        // then
        assertThat(updatedMembership.getPermissions().has(ChannelPermissionType.MEMBER_KICK)).isTrue();
        assertThat(updatedMembership.getChannelId()).isEqualTo(channelId);
    }

    @Test
    void 멤버에게는_권한을_업데이트할_수_없다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(channelId, userId, role, joinedAt);
        ChannelManagePermissions newPermissions = ChannelManagePermissions.ofManager();

        // when & then
        assertThatThrownBy(() -> membership.updatePermissions(newPermissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매니저가 아니라면 채널 권한을 관리할 수 없습니다.");
    }

    @Test
    void 멤버를_매니저로_승격할_수_있다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership memberMembership = ChannelMembership.create(channelId, userId, ChannelRole.MEMBER, joinedAt);
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT, ChannelPermissionType.MEMBER_KICK);
        ChannelManagePermissions promotionPermissions = ChannelManagePermissions.ofManager(perms);

        // when
        ChannelMembership promotedMembership = memberMembership.promoteToManager(promotionPermissions);

        // then
        assertAll(
                () -> assertThat(promotedMembership.getRole()).isEqualTo(ChannelRole.MANAGER),
                () -> assertThat(promotedMembership.getUserId()).isEqualTo(userId),
                () -> assertThat(promotedMembership.getJoinedAt()).isEqualTo(joinedAt),
                () -> assertThat(promotedMembership.getChannelId()).isEqualTo(channelId),
                () -> assertThat(promotedMembership.getPermissions().has(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(promotedMembership.getPermissions().has(ChannelPermissionType.MEMBER_KICK)).isTrue()
        );
    }

    @Test
    void 매니저를_멤버로_강등할_수_있다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership managerMembership = ChannelMembership.create(channelId, userId, ChannelRole.MANAGER, joinedAt);

        // when
        ChannelMembership demotedMembership = managerMembership.demoteToMember();

        // then
        assertAll(
                () -> assertThat(demotedMembership.getRole()).isEqualTo(ChannelRole.MEMBER),
                () -> assertThat(demotedMembership.getUserId()).isEqualTo(userId),
                () -> assertThat(demotedMembership.getChannelId()).isEqualTo(channelId),
                () -> assertThat(demotedMembership.getJoinedAt()).isEqualTo(joinedAt),
                () -> assertThat(demotedMembership.getPermissions().isEmpty()).isTrue()
        );
    }

    @Test
    void 오너는_항상_모든_권한을_가진다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership ownerMembership = ChannelMembership.create(channelId, userId, role, joinedAt);

        // when & then
        assertAll(
                () -> assertThat(ownerMembership.hasPermission(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(ownerMembership.hasPermission(ChannelPermissionType.MEMBER_KICK)).isTrue(),
                () -> assertThat(ownerMembership.hasPermission(ChannelPermissionType.EDIT_CHANNEL_NAME)).isTrue(),
                () -> assertThat(ownerMembership.hasPermission(ChannelPermissionType.MEMBER_INVITE)).isTrue(),
                () -> assertThat(ownerMembership.hasPermission(ChannelPermissionType.MESSAGE_DELETE)).isTrue()
        );
    }

    @Test
    void 매니저는_부여된_권한만_가진다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);

        ChannelMembership managerMembership = ChannelMembership.createManager(channelId, userId, permissions, joinedAt);

        // when & then
        assertAll(
                () -> assertThat(managerMembership.hasPermission(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(managerMembership.hasPermission(ChannelPermissionType.MEMBER_KICK)).isFalse()
        );
    }

    @Test
    void 멤버는_어떤_관리_권한도_가지지_않는다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership memberMembership = ChannelMembership.create(channelId, userId, role, joinedAt);

        // when & then
        assertAll(
                () -> assertThat(memberMembership.hasPermission(ChannelPermissionType.MESSAGE_EDIT)).isFalse(),
                () -> assertThat(memberMembership.hasPermission(ChannelPermissionType.MEMBER_KICK)).isFalse(),
                () -> assertThat(memberMembership.hasPermission(ChannelPermissionType.EDIT_CHANNEL_NAME)).isFalse()
        );
    }

    @Test
    void 오너는_권한이_없는_상태를_확인할_수_없다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());

        // when & then
        assertThat(ownerMembership.lacksPermission(ChannelPermissionType.MESSAGE_EDIT)).isFalse();
    }

    @Test
    void 매니저는_부여되지_않은_권한이_없음을_확인할_수_있다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(managerMembership.lacksPermission(ChannelPermissionType.MESSAGE_EDIT)).isFalse(),
                () -> assertThat(managerMembership.lacksPermission(ChannelPermissionType.MEMBER_KICK)).isTrue()
        );
    }

    @Test
    void 멤버는_모든_관리_권한이_없음을_확인할_수_있다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(memberMembership.lacksPermission(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(memberMembership.lacksPermission(ChannelPermissionType.MEMBER_KICK)).isTrue(),
                () -> assertThat(memberMembership.lacksPermission(ChannelPermissionType.EDIT_CHANNEL_NAME)).isTrue()
        );
    }

    @Test
    void 오너는_채널_이름을_수정할_수_있다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());

        // when & then
        assertThat(ownerMembership.canEditChannelName()).isTrue();
    }

    @Test
    void 채널_이름_수정_권한이_있는_매니저는_채널_이름을_수정할_수_있다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.EDIT_CHANNEL_NAME);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());

        // when & then
        assertThat(managerMembership.canEditChannelName()).isTrue();
    }

    @Test
    void 채널_이름_수정_권한이_없는_매니저는_채널_이름을_수정할_수_없다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(managerMembership.canEditChannelName()).isFalse(),
                () -> assertThat(managerMembership.cannotEditChannelName()).isTrue()
        );
    }

    @Test
    void 멤버는_채널_이름을_수정할_수_없다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(memberMembership.canEditChannelName()).isFalse(),
                () -> assertThat(memberMembership.cannotEditChannelName()).isTrue()
        );
    }

    @Test
    void 오너는_멤버를_강퇴할_수_있다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());

        // when & then
        assertThat(ownerMembership.canKickMember()).isTrue();
    }

    @Test
    void 멤버_강퇴_권한이_있는_매니저는_멤버를_강퇴할_수_있다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MEMBER_KICK);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());

        // when & then
        assertThat(managerMembership.canKickMember()).isTrue();
    }

    @Test
    void 멤버_강퇴_권한이_없는_매니저는_멤버를_강퇴할_수_없다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(managerMembership.canKickMember()).isFalse(),
                () -> assertThat(managerMembership.cannotKickMember()).isTrue()
        );
    }

    @Test
    void 멤버는_다른_멤버를_강퇴할_수_없다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(memberMembership.canKickMember()).isFalse(),
                () -> assertThat(memberMembership.cannotKickMember()).isTrue()
        );
    }

    @Test
    void 오너는_메시지를_삭제할_수_있다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());
        ChannelPolicy policy = ChannelPolicy.defaultPolicy()
                                            .updatePublic(false)
                                            .updateDeleteOwnMessage(false)
                                            .updateEditOwnMessage(false);

        // when & then
        assertThat(ownerMembership.canDeleteMessage(policy)).isTrue();
    }

    @Test
    void 메시지_삭제_권한이_있는_매니저는_메시지를_삭제할_수_있다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_DELETE);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());
        ChannelPolicy policy = ChannelPolicy.defaultPolicy()
                                            .updatePublic(false)
                                            .updateDeleteOwnMessage(false)
                                            .updateEditOwnMessage(false);

        // when & then
        assertThat(managerMembership.canDeleteMessage(policy)).isTrue();
    }

    @Test
    void 메시지_삭제_권한이_없는_매니저는_메시지를_삭제할_수_없다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());
        ChannelPolicy policy = ChannelPolicy.defaultPolicy()
                                            .updatePublic(false)
                                            .updateDeleteOwnMessage(false)
                                            .updateEditOwnMessage(false);

        // when & then
        assertThat(managerMembership.canDeleteMessage(policy)).isFalse();
    }

    @Test
    void 채널_정책이_허용하면_멤버는_자신의_메시지를_삭제할_수_있다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());
        ChannelPolicy policy = ChannelPolicy.defaultPolicy();

        // when & then
        assertThat(memberMembership.canDeleteMessage(policy)).isTrue();
    }

    @Test
    void 채널_정책이_허용하지_않으면_멤버는_자신의_메시지를_삭제할_수_없다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());
        ChannelPolicy policy = ChannelPolicy.defaultPolicy()
                                            .updatePublic(false)
                                            .updateDeleteOwnMessage(false)
                                            .updateEditOwnMessage(false);

        // when & then
        assertThat(memberMembership.canDeleteMessage(policy)).isFalse();
    }

    @Test
    void 오너는_메시지를_수정할_수_있다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());
        ChannelPolicy policy = ChannelPolicy.defaultPolicy()
                                            .updatePublic(false)
                                            .updateDeleteOwnMessage(false)
                                            .updateEditOwnMessage(false);

        // when & then
        assertThat(ownerMembership.canEditMessage(policy)).isTrue();
    }

    @Test
    void 메시지_수정_권한이_있는_매니저는_메시지를_수정할_수_있다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());
        ChannelPolicy policy = ChannelPolicy.defaultPolicy()
                                            .updatePublic(false)
                                            .updateDeleteOwnMessage(false)
                                            .updateEditOwnMessage(false);

        // when & then
        assertThat(managerMembership.canEditMessage(policy)).isTrue();
    }

    @Test
    void 메시지_수정_권한이_없는_매니저는_메시지를_수정할_수_없다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MEMBER_KICK);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());
        ChannelPolicy policy = ChannelPolicy.defaultPolicy()
                                            .updatePublic(false)
                                            .updateDeleteOwnMessage(false)
                                            .updateEditOwnMessage(false);

        // when & then
        assertThat(managerMembership.canEditMessage(policy)).isFalse();
    }

    @Test
    void 채널_정책이_허용하면_멤버는_자신의_메시지를_수정할_수_있다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());
        ChannelPolicy policy = ChannelPolicy.defaultPolicy();

        // when & then
        assertThat(memberMembership.canEditMessage(policy)).isTrue();
    }

    @Test
    void 채널_정책이_허용하지_않으면_멤버는_자신의_메시지를_수정할_수_없다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());
        ChannelPolicy policy = ChannelPolicy.defaultPolicy()
                                            .updatePublic(false)
                                            .updateDeleteOwnMessage(false)
                                            .updateEditOwnMessage(false);

        // when & then
        assertThat(memberMembership.canEditMessage(policy)).isFalse();
    }

    @Test
    void 오너는_멤버를_초대할_수_있다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());

        // when & then
        assertThat(ownerMembership.canInviteMember()).isTrue();
    }

    @Test
    void 멤버_초대_권한이_있는_매니저는_멤버를_초대할_수_있다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MEMBER_INVITE);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());

        // when & then
        assertThat(managerMembership.canInviteMember()).isTrue();
    }

    @Test
    void 멤버_초대_권한이_없는_매니저는_멤버를_초대할_수_없다() {
        // given
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        ChannelMembership managerMembership = ChannelMembership.createManager(
                ChannelId.create(10L), UserId.create(1L), permissions, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(managerMembership.canInviteMember()).isFalse(),
                () -> assertThat(managerMembership.cannotInviteMember()).isTrue()
        );
    }

    @Test
    void 멤버는_다른_멤버를_초대할_수_없다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(memberMembership.canInviteMember()).isFalse(),
                () -> assertThat(memberMembership.cannotInviteMember()).isTrue()
        );
    }

    @Test
    void 오너는_역할을_관리할_수_있다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(ownerMembership.canManageRole()).isTrue(),
                () -> assertThat(ownerMembership.cannotManageRole()).isFalse()
        );
    }

    @Test
    void 매니저는_역할을_관리할_수_없다() {
        // given
        ChannelMembership managerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MANAGER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(managerMembership.canManageRole()).isFalse(),
                () -> assertThat(managerMembership.cannotManageRole()).isTrue()
        );
    }

    @Test
    void 멤버는_역할을_관리할_수_없다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(memberMembership.canManageRole()).isFalse(),
                () -> assertThat(memberMembership.cannotManageRole()).isTrue()
        );
    }

    @Test
    void 오너는_권한을_관리할_수_있다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(ownerMembership.canManagePermission()).isTrue(),
                () -> assertThat(ownerMembership.cannotManagePermission()).isFalse()
        );
    }

    @Test
    void 매니저는_권한을_관리할_수_없다() {
        // given
        ChannelMembership managerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MANAGER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(managerMembership.canManagePermission()).isFalse(),
                () -> assertThat(managerMembership.cannotManagePermission()).isTrue()
        );
    }

    @Test
    void 멤버는_권한을_관리할_수_없다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(memberMembership.canManagePermission()).isFalse(),
                () -> assertThat(memberMembership.cannotManagePermission()).isTrue()
        );
    }

    @Test
    void 오너는_채널을_삭제할_수_있다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(ownerMembership.canDeleteChannel()).isTrue(),
                () -> assertThat(ownerMembership.cannotDeleteChannel()).isFalse()
        );
    }

    @Test
    void 매니저는_채널을_삭제할_수_없다() {
        // given
        ChannelMembership managerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MANAGER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(managerMembership.canDeleteChannel()).isFalse(),
                () -> assertThat(managerMembership.cannotDeleteChannel()).isTrue()
        );
    }

    @Test
    void 멤버는_채널을_삭제할_수_없다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(memberMembership.canDeleteChannel()).isFalse(),
                () -> assertThat(memberMembership.cannotDeleteChannel()).isTrue()
        );
    }

    @Test
    void 오너는_채널_정책을_변경할_수_있다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(ownerMembership.canChangeChannelPolicy()).isTrue(),
                () -> assertThat(ownerMembership.cannotChangeChannelPolicy()).isFalse()
        );
    }

    @Test
    void 매니저는_채널_정책을_변경할_수_없다() {
        // given
        ChannelMembership managerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MANAGER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(managerMembership.canChangeChannelPolicy()).isFalse(),
                () -> assertThat(managerMembership.cannotChangeChannelPolicy()).isTrue()
        );
    }

    @Test
    void 멤버는_채널_정책을_변경할_수_없다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(memberMembership.canChangeChannelPolicy()).isFalse(),
                () -> assertThat(memberMembership.cannotChangeChannelPolicy()).isTrue()
        );
    }

    @Test
    void 역할이_매니저인지_확인할_수_있다() {
        // given
        ChannelMembership managerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MANAGER, LocalDateTime.now());
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(2L), ChannelRole.OWNER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(managerMembership.isManager()).isTrue(),
                () -> assertThat(ownerMembership.isManager()).isFalse()
        );
    }

    @Test
    void 역할이_멤버인지_확인할_수_있다() {
        // given
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.MEMBER, LocalDateTime.now());
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(2L), ChannelRole.OWNER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(memberMembership.isMember()).isTrue(),
                () -> assertThat(ownerMembership.isMember()).isFalse()
        );
    }

    @Test
    void 역할이_오너인지_확인할_수_있다() {
        // given
        ChannelMembership ownerMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(1L), ChannelRole.OWNER, LocalDateTime.now());
        ChannelMembership memberMembership = ChannelMembership.create(
                ChannelId.create(10L), UserId.create(2L), ChannelRole.MEMBER, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(ownerMembership.isOwner()).isTrue(),
                () -> assertThat(memberMembership.isOwner()).isFalse()
        );
    }

    @Test
    void 오너에게는_권한을_업데이트할_수_없다() {
        // given
        ChannelId channelId = ChannelId.create(10L);
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership ownerMembership = ChannelMembership.create(channelId, userId, role, joinedAt);
        ChannelManagePermissions newPermissions = ChannelManagePermissions.ofManager();

        // when & then
        assertThatThrownBy(() -> ownerMembership.updatePermissions(newPermissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매니저가 아니라면 채널 권한을 관리할 수 없습니다.");
    }
}
