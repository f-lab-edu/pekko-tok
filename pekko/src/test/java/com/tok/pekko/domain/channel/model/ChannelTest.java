package com.tok.pekko.domain.channel.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelTest {

    @Test
    void 유효한_값으로_채널을_초기화할_수_있다() {
        // given
        String name = "general";
        Long creatorId = 1L;
        ChannelPolicy channelPolicy = ChannelPolicy.defaultPolicy();
        LocalDateTime createdAt = LocalDateTime.now();

        // when
        Channel channel = Channel.create(name, creatorId, channelPolicy, createdAt);

        // then
        assertAll(
                () -> assertThat(channel).isNotNull(),
                () -> assertThat(channel.getName()).isEqualTo(name),
                () -> assertThat(channel.getCreatorId()).isEqualTo(UserId.create(creatorId)),
                () -> assertThat(channel.getChannelPolicy()).isEqualTo(channelPolicy),
                () -> assertThat(channel.getCreatedAt()).isEqualTo(createdAt),
                () -> assertThat(channel.getMemberships()).isEmpty()
        );
    }

    @ParameterizedTest(name = "{0}일 때 초기화에 실패한다")
    @NullAndEmptySource
    void 유효하지_않은_채널_이름으로는_초기화할_수_없다(String name) {
        // when & then
        assertThatThrownBy(() -> Channel.create(name, 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채널 이름은 필수입니다.");
    }

    @Test
    void 채널을_초기화한다() {
        // given
        Long channelId = 1L;
        String name = "general";
        Long creatorId = 1L;
        ChannelPolicy channelPolicy = ChannelPolicy.defaultPolicy();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        LocalDateTime createdAt = LocalDateTime.now();

        // when
        Channel channel = Channel.create(channelId, name, creatorId, channelPolicy, memberships, createdAt);

        // then
        assertAll(
                () -> assertThat(channel).isNotNull(),
                () -> assertThat(channel.getChannelId()).isEqualTo(ChannelId.create(channelId)),
                () -> assertThat(channel.getName()).isEqualTo(name),
                () -> assertThat(channel.getCreatorId()).isEqualTo(UserId.create(creatorId))
        );
    }

    @Test
    void 채널의_공개_여부를_확인할_수_있다() {
        // given
        Channel publicChannel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        ChannelPolicy privatePolicy = new ChannelPolicy(true, true, false);
        Channel privateChannel = Channel.create("private", 1L, privatePolicy, LocalDateTime.now());

        // when & then
        assertAll(
                () -> assertThat(publicChannel.isPublic()).isTrue(),
                () -> assertThat(privateChannel.isPublic()).isFalse()
        );
    }

    @Test
    void 공개_채널에_멤버가_참여할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        // when
        channel.joinMember(userId, role, joinedAt);

        // then
        assertThat(channel.getMemberships()).containsKey(userId);
    }

    @Test
    void 비공개_채널에는_직접_참여할_수_없다() {
        // given
        ChannelPolicy privatePolicy = new ChannelPolicy(true, true, false);
        Channel channel = Channel.create("private", 1L, privatePolicy, LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> channel.joinMember(userId, role, joinedAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비공개 채널입니다. 초대를 통해 참여할 수 있습니다.");
    }

    @Test
    void 이미_참여한_멤버는_다시_참여할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThatThrownBy(() -> channel.joinMember(userId, role, joinedAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 채널에 참여한 사용자입니다.");
    }

    @Test
    void 채널에_멤버를_초대할_수_있다() {
        // given
        ChannelPolicy privatePolicy = new ChannelPolicy(true, true, false);
        Channel channel = Channel.create("private", 1L, privatePolicy, LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();

        // when
        channel.inviteMember(userId, role, joinedAt);

        // then
        assertThat(channel.getMemberships()).containsKey(userId);
    }

    @Test
    void 초대된_멤버도_중복으로_초대할_수_없다() {
        // given
        ChannelPolicy privatePolicy = new ChannelPolicy(true, true, false);
        Channel channel = Channel.create("private", 1L, privatePolicy, LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.inviteMember(userId, role, joinedAt);

        // when & then
        assertThatThrownBy(() -> channel.inviteMember(userId, role, joinedAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 채널에 참여한 사용자입니다.");
    }

    @Test
    void 멤버를_매니저로_승격할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        Set<ChannelPermissionType> perms = EnumSet.of(
                ChannelPermissionType.MESSAGE_EDIT,
                ChannelPermissionType.MEMBER_KICK
        );
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);

        // when
        channel.promoteToManager(userId, permissions);

        // then
        ChannelMembership membership = channel.getMemberships().get(userId);
        assertAll(
                () -> assertThat(membership.isManager()).isTrue(),
                () -> assertThat(membership.getPermissions().has(ChannelPermissionType.MESSAGE_EDIT)).isTrue()
        );
    }

    @Test
    void 존재하지_않는_멤버를_승격할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);

        // when & then
        assertThatThrownBy(() -> channel.promoteToManager(userId, permissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채널 멤버를 찾을 수 없습니다.");
    }

    @Test
    void 매니저를_멤버로_강등할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when
        channel.demoteToMember(userId);

        // then
        ChannelMembership membership = channel.getMemberships().get(userId);
        assertThat(membership.isMember()).isTrue();
    }

    @Test
    void 이미_멤버인_경우_강등할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThatThrownBy(() -> channel.demoteToMember(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 멤버입니다.");
    }

    @Test
    void 오너는_강등할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThatThrownBy(() -> channel.demoteToMember(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오너는 강등할 수 없습니다.");
    }

    @Test
    void 존재하지_않는_멤버를_강등할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);

        // when & then
        assertThatThrownBy(() -> channel.demoteToMember(userId))
                .isInstanceOf(Channel.ChannelMembershipNotFoundException.class)
                .hasMessage("채널 멤버를 찾을 수 없습니다.");
    }

    @Test
    void 매니저의_권한을_업데이트할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId managerId = UserId.create(2L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(managerId, role, joinedAt);

        Set<ChannelPermissionType> newPerms = EnumSet.of(
                ChannelPermissionType.MESSAGE_EDIT,
                ChannelPermissionType.MEMBER_KICK,
                ChannelPermissionType.MEMBER_INVITE
        );
        ChannelManagePermissions newPermissions = ChannelManagePermissions.ofManager(newPerms);

        // when
        channel.updateManagerPermissions(managerId, newPermissions);

        // then
        ChannelMembership membership = channel.getMemberships().get(managerId);
        assertThat(membership.getPermissions().has(ChannelPermissionType.MEMBER_INVITE)).isTrue();
    }

    @Test
    void 매니저가_아닌_사용자의_권한은_업데이트할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        Set<ChannelPermissionType> newPerms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions newPermissions = ChannelManagePermissions.ofManager(newPerms);

        // when & then
        assertThatThrownBy(() -> channel.updateManagerPermissions(userId, newPermissions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 멤버는 매니저가 아닙니다.");
    }

    @Test
    void 매니저에게_권한을_추가할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId managerId = UserId.create(2L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(managerId, role, joinedAt);

        // when
        channel.addPermissionToManager(managerId, ChannelPermissionType.MEMBER_KICK);

        // then
        ChannelMembership membership = channel.getMemberships().get(managerId);
        assertThat(membership.getPermissions().has(ChannelPermissionType.MEMBER_KICK)).isTrue();
    }

    @Test
    void 멤버에게_권한을_추가할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThatThrownBy(() -> channel.addPermissionToManager(userId, ChannelPermissionType.MESSAGE_EDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 멤버는 매니저가 아닙니다.");
    }

    @Test
    void 매니저에게서_권한을_제거할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId managerId = UserId.create(2L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(managerId, role, joinedAt);

        // when
        channel.removePermissionFromManager(managerId, ChannelPermissionType.MESSAGE_EDIT);

        // then
        ChannelMembership membership = channel.getMemberships().get(managerId);
        assertThat(membership.getPermissions().isEmpty()).isTrue();
    }

    @Test
    void 멤버를_채널에서_제거할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when
        channel.leaveMember(userId);

        // then
        assertThat(channel.getMemberships()).doesNotContainKey(userId);
    }

    @Test
    void 오너는_채널에서_나갈_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThatThrownBy(() -> channel.leaveMember(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오너는 채널에서 나갈 수 없습니다.");
    }

    @Test
    void 멤버는_채널정책에_따라_자신의_메시지를_수정할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThat(channel.canMemberEditMessage(userId)).isTrue();
    }

    @Test
    void 멤버는_채널정책에_따라_자신의_메시지를_삭제할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThat(channel.canMemberDeleteMessage(userId)).isTrue();
    }

    @Test
    void 오너는_채널명을_변경할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThat(channel.canUserEditChannelName(userId)).isTrue();
    }

    @Test
    void 매니저는_EDIT_CHANNEL_NAME_권한으로만_채널명_변경이_가능하다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId managerId = UserId.create(2L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(managerId, role, joinedAt);

        // when & then
        assertThat(channel.canUserEditChannelName(managerId)).isFalse();
    }

    @Test
    void 오너는_멤버를_강퇴할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThat(channel.canUserKickMember(userId)).isTrue();
    }

    @Test
    void 오너는_멤버를_초대할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThat(channel.canUserInviteMember(userId)).isTrue();
    }

    @Test
    void 매니저는_MEMBER_INVITE_권한으로만_멤버_초대가_가능하다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId managerId = UserId.create(2L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(managerId, role, joinedAt);

        // when & then
        assertThat(channel.canUserInviteMember(managerId)).isFalse();
    }

    @Test
    void 오너는_멤버_역할을_관리할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThat(channel.canUserMemberRoleManagement(userId)).isTrue();
    }

    @Test
    void 매니저는_멤버_역할을_관리할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId managerId = UserId.create(2L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(managerId, role, joinedAt);

        // when & then
        assertThat(channel.canUserMemberRoleManagement(managerId)).isFalse();
    }

    @Test
    void 오너는_권한을_관리할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThat(channel.canUserPermissionManagement(userId)).isTrue();
    }

    @Test
    void 매니저는_권한을_관리할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId managerId = UserId.create(2L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(managerId, role, joinedAt);

        // when & then
        assertThat(channel.canUserPermissionManagement(managerId)).isFalse();
    }

    @Test
    void 오너는_채널을_삭제할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThat(channel.canUserDeleteChannel(userId)).isTrue();
    }

    @Test
    void 매니저는_채널을_삭제할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId managerId = UserId.create(2L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(managerId, role, joinedAt);

        // when & then
        assertThat(channel.canUserDeleteChannel(managerId)).isFalse();
    }

    @Test
    void 채널_이름을_변경한다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        String newName = "random";

        // when
        Channel changedChannel = channel.changeName(newName);

        // then
        assertAll(
                () -> assertThat(changedChannel.getName()).isEqualTo(newName),
                () -> assertThat(changedChannel.getCreatorId()).isEqualTo(channel.getCreatorId()),
                () -> assertThat(changedChannel.getChannelPolicy()).isEqualTo(channel.getChannelPolicy())
        );
    }

    @Test
    void 채널_정책을_변경한다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        ChannelPolicy newPolicy = new ChannelPolicy(false, false, false);

        // when
        Channel actual = channel.changeChannelPolicy(newPolicy);

        // then
        assertThat(actual.getChannelPolicy()).isEqualTo(newPolicy);
    }

    @Test
    void 오너는_채널_정책을_변경할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.OWNER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThat(channel.canUserChangeChannelPolicy(userId)).isTrue();
    }

    @Test
    void 매니저는_채널_정책을_변경할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId managerId = UserId.create(2L);
        ChannelRole role = ChannelRole.MANAGER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(managerId, role, joinedAt);

        // when & then
        assertThat(channel.canUserChangeChannelPolicy(managerId)).isFalse();
    }

    @Test
    void 멤버는_채널_정책을_변경할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);
        ChannelRole role = ChannelRole.MEMBER;
        LocalDateTime joinedAt = LocalDateTime.now();
        channel.joinMember(userId, role, joinedAt);

        // when & then
        assertThat(channel.canUserChangeChannelPolicy(userId)).isFalse();
    }

    @Test
    void 존재하지_않는_멤버는_채널_정책_변경_권한을_확인할_수_없다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        UserId userId = UserId.create(2L);

        // when & then
        assertThatThrownBy(() -> channel.canUserChangeChannelPolicy(userId))
                .isInstanceOf(Channel.ChannelMembershipNotFoundException.class)
                .hasMessage("채널 멤버를 찾을 수 없습니다.");
    }
}
