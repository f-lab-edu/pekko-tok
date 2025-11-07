package com.tok.pekko.domain.channel.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

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
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        // when
        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);

        // then
        assertAll(
                () -> assertThat(membership).isNotNull(),
                () -> assertThat(membership.getId()).isEqualTo(ChannelMembershipId.EMPTY_CHANNEL_MEMBERSHIP_ID),
                () -> assertThat(membership.getUserId()).isEqualTo(userId),
                () -> assertThat(membership.getRole()).isEqualTo(role),
                () -> assertThat(membership.getJoinedAt()).isEqualTo(joinedAt)
        );
    }

    @Test
    void null_사용자_ID로는_채널_참여자를_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ChannelMembership.create(null, ChannelRole.MEMBER, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 필수입니다.");
    }

    @Test
    void 비어_있는_사용자_ID로는_채널_참여자를_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ChannelMembership.create(UserId.EMPTY_USER_ID, ChannelRole.MEMBER, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 필수입니다.");
    }

    @Test
    void 멤버_역할로_초기화하면_아무_권한도_가지지_못한다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.getPermissions().isEmpty()).isTrue();
    }

    @Test
    void 오너_역할로_초기화하면_모든_권한이_기본적으로_부여되므로_아무_권한도_가지지_않는다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.OWNER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.getPermissions().isEmpty()).isTrue();
    }

    @Test
    void 매니저_역할로_초기화하면_메시지_수정_권한을_가진다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.getPermissions().has(ChannelPermissionType.MESSAGE_EDIT)).isTrue();
    }

    @Test
    void 매니저_역할로_명시적_권한을_지정해_초기화할_수_있다() {
        // given
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();
        Set<ChannelPermissionType> perms = EnumSet.of(
                ChannelPermissionType.MESSAGE_EDIT,
                ChannelPermissionType.MEMBER_KICK
        );
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);

        // when
        ChannelMembership membership = ChannelMembership.createManager(userId, permissions, joinedAt);

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
        Long userId = 1L;
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        Set<ChannelPermissionType> perms = EnumSet.of(
                ChannelPermissionType.MESSAGE_EDIT,
                ChannelPermissionType.MEMBER_KICK
        );
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);

        // when
        ChannelMembership membership = ChannelMembership.create(memberId, userId, role, permissions, joinedAt);

        // then
        assertAll(
                () -> assertThat(membership.getId()).isEqualTo(ChannelMembershipId.create(memberId)),
                () -> assertThat(membership.getUserId()).isEqualTo(UserId.create(userId)),
                () -> assertThat(membership.getRole()).isEqualTo(role),
                () -> assertThat(membership.getPermissions().has(ChannelPermissionType.MESSAGE_EDIT)).isTrue()
        );
    }

    @Test
    void 같은_채널_참여자_ID를_가진_채널_참여자는_동등하다() {
        // given
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership1 = ChannelMembership.create(1L, userId.getValue(), ChannelRole.MEMBER,
                ChannelManagePermissions.ofMember(), joinedAt);
        ChannelMembership membership2 = ChannelMembership.create(1L, userId.getValue(), ChannelRole.OWNER,
                ChannelManagePermissions.ofOwner(), joinedAt);

        // when & then
        assertAll(
                () -> assertThat(membership1).isEqualTo(membership2),
                () -> assertThat(membership1).hasSameHashCodeAs(membership2)
        );
    }

    @Test
    void 다른_채널_참여자_ID를_가진_채널_참여자는_동등하지_않다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership1 = ChannelMembership.create(1L, userId.getValue(), role,
                ChannelManagePermissions.ofMember(), joinedAt);
        ChannelMembership membership2 = ChannelMembership.create(2L, userId.getValue(), role,
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
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);
        Long assignedId = 100L;

        // when
        ChannelMembership actual = membership.withAssignedId(assignedId);

        // then
        assertAll(
                () -> assertThat(actual.getId()).isEqualTo(ChannelMembershipId.create(assignedId)),
                () -> assertThat(actual.getUserId()).isEqualTo(userId),
                () -> assertThat(actual.getRole()).isEqualTo(role),
                () -> assertThat(actual.getJoinedAt()).isEqualTo(joinedAt)
        );
    }

    @Test
    void 매니저에게는_권한을_업데이트할_수_있다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);
        Set<ChannelPermissionType> newPerms = EnumSet.of(
                ChannelPermissionType.MESSAGE_EDIT,
                ChannelPermissionType.MEMBER_KICK
        );
        ChannelManagePermissions newPermissions = ChannelManagePermissions.ofManager(newPerms);

        // when
        ChannelMembership updatedMembership = membership.updatePermissions(newPermissions);

        // then
        assertThat(updatedMembership.getPermissions().has(ChannelPermissionType.MEMBER_KICK)).isTrue();
    }

    @Test
    void 멤버에게는_권한을_업데이트할_수_없다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);
        ChannelManagePermissions newPermissions = ChannelManagePermissions.ofManager();

        // when & then
        assertThatThrownBy(() -> membership.updatePermissions(newPermissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매니저가 아니라면 채널 권한을 관리할 수 없습니다.");
    }

    @Test
    void 오너에게는_권한을_업데이트할_수_없다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);
        ChannelManagePermissions newPermissions = ChannelManagePermissions.ofManager();

        // when & then
        assertThatThrownBy(() -> membership.updatePermissions(newPermissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매니저가 아니라면 채널 권한을 관리할 수 없습니다.");
    }

    @Test
    void 매니저에게는_권한을_추가할_수_있다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);

        // when
        ChannelMembership addedMembership = membership.addPermission(ChannelPermissionType.MEMBER_KICK);

        // then
        assertThat(addedMembership.getPermissions().has(ChannelPermissionType.MEMBER_KICK)).isTrue();
    }

    @Test
    void 멤버에게는_권한을_추가할_수_없다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);

        // when & then
        assertThatThrownBy(() -> membership.addPermission(ChannelPermissionType.MESSAGE_EDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매니저가 아니라면 채널 권한을 관리할 수 없습니다.");
    }

    @Test
    void 매니저에게는_권한을_제거할_수_있다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);

        // when
        ChannelMembership removedMembership = membership.removePermission(ChannelPermissionType.MESSAGE_EDIT);

        // then
        assertThat(removedMembership.getPermissions().isEmpty()).isTrue();
    }

    @Test
    void 멤버는_권한을_제거할_수_없다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(userId, role, joinedAt);

        // when & then
        assertThatThrownBy(() -> membership.removePermission(ChannelPermissionType.MESSAGE_EDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("매니저가 아니라면 채널 권한을 관리할 수 없습니다.");
    }

    @Test
    void 매니저_역할을_멤버_역할로_변경할_수_있다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole oldRole = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(userId, oldRole, joinedAt);

        // when
        ChannelMembership changedMembership = membership.changeRole(ChannelRole.MEMBER, EnumSet.noneOf(ChannelPermissionType.class));

        // then
        assertThat(changedMembership.getRole()).isEqualTo(ChannelRole.MEMBER);
    }

    @Test
    void 멤버_역할을_매니저_역할로_변경하면서_권한을_지정할_수_있다() {
        // given
        UserId userId = UserId.create(1L);
        ChannelRole oldRole = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership membership = ChannelMembership.create(userId, oldRole, joinedAt);
        Set<ChannelPermissionType> newPermissions = EnumSet.of(
                ChannelPermissionType.MESSAGE_EDIT,
                ChannelPermissionType.MEMBER_KICK
        );

        // when
        ChannelMembership changedMembership = membership.changeRole(ChannelRole.MANAGER, newPermissions);

        // then
        assertAll(
                () -> assertThat(changedMembership.getRole()).isEqualTo(ChannelRole.MANAGER),
                () -> assertThat(changedMembership.getPermissions().has(ChannelPermissionType.MEMBER_KICK)).isTrue()
        );
    }

    @Test
    void 오너는_모든_권한을_가진다() {
        // given
        ChannelPolicy channelPolicy = new ChannelPolicy(false, false, false);

        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.OWNER,
                LocalDateTime.now()
        );

        // then
        assertAll(
                () -> assertThat(membership.hasPermission(ChannelPermissionType.EDIT_CHANNEL_NAME)).isTrue(),
                () -> assertThat(membership.hasPermission(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(membership.hasPermission(ChannelPermissionType.MESSAGE_DELETE)).isTrue(),
                () -> assertThat(membership.hasPermission(ChannelPermissionType.MEMBER_INVITE)).isTrue(),
                () -> assertThat(membership.hasPermission(ChannelPermissionType.MEMBER_KICK)).isTrue(),
                () -> assertThat(membership.canEditChannelName()).isTrue(),
                () -> assertThat(membership.canKickMember()).isTrue(),
                () -> assertThat(membership.canDeleteMessage(channelPolicy)).isTrue(),
                () -> assertThat(membership.canEditMessage(channelPolicy)).isTrue(),
                () -> assertThat(membership.canInviteMember()).isTrue(),
                () -> assertThat(membership.canMemberRoleManagement()).isTrue(),
                () -> assertThat(membership.canPermissionManagement()).isTrue(),
                () -> assertThat(membership.canDeleteChannel()).isTrue(),
                () -> assertThat(membership.canChangeChannelPolicy()).isTrue()
        );
    }

    @Test
    void 매니저는_가진_권한만_반영된다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );

        // then
        assertAll(
                () -> assertThat(membership.hasPermission(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(membership.hasPermission(ChannelPermissionType.MESSAGE_DELETE)).isFalse()
        );
    }

    @Test
    void 매니저는_메시지_삭제_권한이_있어야_메시지_삭제가_가능하다() {
        // given
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );
        ChannelPolicy policy = new ChannelPolicy(true, true, true);

        // when & then
        assertThat(membership.canDeleteMessage(policy)).isFalse();
    }

    @Test
    void 멤버는_채널_정책에_따라_자신의_메시지만_삭제할_수_있다() {
        // given
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );
        ChannelPolicy allowPolicy = new ChannelPolicy(true, true, true);
        ChannelPolicy denyPolicy = new ChannelPolicy(true, false, true);

        // when & then
        assertAll(
                () -> assertThat(membership.canDeleteMessage(allowPolicy)).isTrue(),
                () -> assertThat(membership.canDeleteMessage(denyPolicy)).isFalse()
        );
    }

    @Test
    void 멤버는_채널_정책에_따라_자신의_메시지만_수정할_수_있다() {
        // given
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );
        ChannelPolicy allowPolicy = new ChannelPolicy(true, false, true);
        ChannelPolicy denyPolicy = new ChannelPolicy(false, true, true);

        // when & then
        assertAll(
                () -> assertThat(membership.canEditMessage(allowPolicy)).isTrue(),
                () -> assertThat(membership.canEditMessage(denyPolicy)).isFalse()
        );
    }

    @Test
    void 매니저는_멤버_초대_권한이_있어야_멤버_초대가_가능하다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.canInviteMember()).isFalse();
    }

    @Test
    void 매니저는_멤버_역할을_관리할_수_없다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.canMemberRoleManagement()).isFalse();
    }

    @Test
    void 해당_채널_참여자가_매니저_역할인지_확인한다() {
        // given
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership managerMembership = ChannelMembership.create(userId, ChannelRole.MANAGER, joinedAt);
        ChannelMembership memberMembership = ChannelMembership.create(userId, ChannelRole.MEMBER, joinedAt);
        ChannelMembership ownerMembership = ChannelMembership.create(userId, ChannelRole.OWNER, joinedAt);

        // when & then
        assertAll(
                () -> assertThat(managerMembership.isManager()).isTrue(),
                () -> assertThat(memberMembership.isManager()).isFalse(),
                () -> assertThat(ownerMembership.isManager()).isFalse()
        );
    }

    @Test
    void 해당_채널_참여자가_멤버_역할인지_확인한다() {
        // given
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership memberMembership = ChannelMembership.create(userId, ChannelRole.MEMBER, joinedAt);
        ChannelMembership managerMembership = ChannelMembership.create(userId, ChannelRole.MANAGER, joinedAt);
        ChannelMembership ownerMembership = ChannelMembership.create(userId, ChannelRole.OWNER, joinedAt);

        // when & then
        assertAll(
                () -> assertThat(memberMembership.isMember()).isTrue(),
                () -> assertThat(managerMembership.isMember()).isFalse(),
                () -> assertThat(ownerMembership.isMember()).isFalse()
        );
    }

    @Test
    void 해당_채널_참여자가_오너_역할인지_확인한다() {
        // given
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();

        ChannelMembership ownerMembership = ChannelMembership.create(userId, ChannelRole.OWNER, joinedAt);
        ChannelMembership memberMembership = ChannelMembership.create(userId, ChannelRole.MEMBER, joinedAt);
        ChannelMembership managerMembership = ChannelMembership.create(userId, ChannelRole.MANAGER, joinedAt);

        // when & then
        assertAll(
                () -> assertThat(ownerMembership.isOwner()).isTrue(),
                () -> assertThat(memberMembership.isOwner()).isFalse(),
                () -> assertThat(managerMembership.isOwner()).isFalse()
        );
    }

    @Test
    void 멤버를_매니저로_승격할_수_있다() {
        // given
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();
        ChannelMembership memberMembership = ChannelMembership.create(userId, ChannelRole.MEMBER, joinedAt);
        Set<ChannelPermissionType> perms = EnumSet.of(
                ChannelPermissionType.MESSAGE_EDIT,
                ChannelPermissionType.MEMBER_KICK
        );
        ChannelManagePermissions promotionPermissions = ChannelManagePermissions.ofManager(perms);

        // when
        ChannelMembership promotedMembership = memberMembership.promoteToManager(promotionPermissions);

        // then
        assertAll(
                () -> assertThat(promotedMembership.getRole()).isEqualTo(ChannelRole.MANAGER),
                () -> assertThat(promotedMembership.getUserId()).isEqualTo(userId),
                () -> assertThat(promotedMembership.getJoinedAt()).isEqualTo(joinedAt),
                () -> assertThat(promotedMembership.getPermissions().has(ChannelPermissionType.MESSAGE_EDIT)).isTrue(),
                () -> assertThat(promotedMembership.getPermissions().has(ChannelPermissionType.MEMBER_KICK)).isTrue()
        );
    }

    @Test
    void 매니저를_멤버로_강등할_수_있다() {
        // given
        UserId userId = UserId.create(1L);
        LocalDateTime joinedAt = LocalDateTime.now();
        ChannelMembership managerMembership = ChannelMembership.create(userId, ChannelRole.MANAGER, joinedAt);

        // when
        ChannelMembership demotedMembership = managerMembership.demoteToMember();

        // then
        assertAll(
                () -> assertThat(demotedMembership.getRole()).isEqualTo(ChannelRole.MEMBER),
                () -> assertThat(demotedMembership.getUserId()).isEqualTo(userId),
                () -> assertThat(demotedMembership.getJoinedAt()).isEqualTo(joinedAt),
                () -> assertThat(demotedMembership.getPermissions().isEmpty()).isTrue()
        );
    }

    @Test
    void 매니저는_멤버_강퇴_권한이_있어야_멤버_강퇴가_가능하다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.canKickMember()).isFalse();
    }

    @Test
    void 매니저는_권한을_관리할_수_없다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.canPermissionManagement()).isFalse();
    }

    @Test
    void 멤버는_권한을_관리할_수_없다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.canPermissionManagement()).isFalse();
    }

    @Test
    void 매니저는_채널을_삭제할_수_없다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.canDeleteChannel()).isFalse();
    }

    @Test
    void 멤버는_채널을_삭제할_수_없다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.canDeleteChannel()).isFalse();
    }

    @Test
    void 매니저는_채널_정책을_변경할_수_없다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.canChangeChannelPolicy()).isFalse();
    }

    @Test
    void 멤버는_채널_정책을_변경할_수_없다() {
        // when
        ChannelMembership membership = ChannelMembership.create(
                UserId.create(1L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );

        // then
        assertThat(membership.canChangeChannelPolicy()).isFalse();
    }
}
