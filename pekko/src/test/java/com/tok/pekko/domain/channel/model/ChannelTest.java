package com.tok.pekko.domain.channel.model;

import com.tok.pekko.domain.channel.model.Channel.ChannelMembershipNotFoundException;
import com.tok.pekko.domain.channel.model.Channel.ChannelMembershipOperationForbiddenException;
import com.tok.pekko.domain.channel.model.Channel.ChannelOperationForbiddenException;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChatMessage;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelTest {

    @Test
    void 유효한_값으로_채널을_초기화할_수_있다() {
        // given
        String name = "general";
        Long creatorId = 1L;
        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        LocalDateTime createdAt = LocalDateTime.now();

        // when
        Channel channel = Channel.create(name, creatorId, policy, createdAt);

        // then
        assertAll(
                () -> assertThat(channel).isNotNull(),
                () -> assertThat(channel.getName()).isEqualTo(name),
                () -> assertThat(channel.getCreatorId()).isEqualTo(UserId.create(creatorId)),
                () -> assertThat(channel.getChannelPolicy()).isEqualTo(policy),
                () -> assertThat(channel.getMemberships()).isEmpty(),
                () -> assertThat(channel.getCreatedAt()).isEqualTo(createdAt)
        );
    }

    @ParameterizedTest(name = "{0}일 때 초기화에 실패한다")
    @NullAndEmptySource
    void 유효하지_않은_채널_이름으로는_초기화할_수_없다(String name) {
        // given
        Long creatorId = 1L;
        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        LocalDateTime createdAt = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> Channel.create(name, creatorId, policy, createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채널 이름은 필수입니다.");
    }

    @Test
    void 영속화된_채널을_초기화할_수_있다() {
        // given
        Long channelId = 1L;
        String name = "general";
        Long creatorId = 1L;
        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        LocalDateTime createdAt = LocalDateTime.now();

        // when
        Channel channel = Channel.create(channelId, name, creatorId, policy, memberships, createdAt);

        // then
        assertAll(
                () -> assertThat(channel).isNotNull(),
                () -> assertThat(channel.getChannelId().getValue()).isEqualTo(channelId),
                () -> assertThat(channel.getName()).isEqualTo(name),
                () -> assertThat(channel.getCreatorId()).isEqualTo(UserId.create(creatorId))
        );
    }

    @Test
    void 채널에_ID를_할당할_수_있다() {
        // given
        String name = "general";
        Long creatorId = 1L;
        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        LocalDateTime createdAt = LocalDateTime.now();
        Channel channel = Channel.create(name, creatorId, policy, createdAt);
        Long assignedId = 100L;

        // when
        Channel channelWithId = channel.withAssignedId(assignedId);

        // then
        assertAll(
                () -> assertThat(channelWithId.getChannelId().getValue()).isEqualTo(assignedId),
                () -> assertThat(channelWithId.getName()).isEqualTo(name),
                () -> assertThat(channelWithId.getCreatorId()).isEqualTo(UserId.create(creatorId))
        );
    }

    @Test
    void 오너는_멤버를_매니저로_승격할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId memberId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        ChannelMembership targetMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(memberId, targetMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when
        ChannelMembership promotedMembership = channel.promoteToManager(ownerId, memberId);

        // then
        assertAll(
                () -> assertThat(promotedMembership).isNotNull(),
                () -> assertThat(promotedMembership.getRole()).isEqualTo(ChannelRole.MANAGER)
        );
    }

    @Test
    void 권한이_없는_사용자는_멤버를_승격할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        UserId targetMemberId = UserId.create(3L);

        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        ChannelMembership targetMembership = ChannelMembership.create(3L, 1L, targetMemberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);

        memberships.put(memberId, memberMembership);
        memberships.put(targetMemberId, targetMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.promoteToManager(memberId, targetMemberId))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("역할을 변경할 권한이 없습니다.");
    }

    @Test
    void 채널에_참여하지_않은_사용자는_멤버를_승격할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId nonMemberId = UserId.create(999L);
        UserId targetMemberId = UserId.create(3L);

        ChannelMembership targetMembership = ChannelMembership.create(3L, 1L, targetMemberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(targetMemberId, targetMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.promoteToManager(nonMemberId, targetMemberId))
                .isInstanceOf(ChannelMembershipNotFoundException.class)
                .hasMessage("채널 멤버를 찾을 수 없습니다.");
    }

    @Test
    void 존재하지_않는_멤버를_승격할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId nonExistentMemberId = UserId.create(999L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        memberships.put(ownerId, ownerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.promoteToManager(ownerId, nonExistentMemberId))
                .isInstanceOf(ChannelMembershipNotFoundException.class)
                .hasMessage("채널 멤버를 찾을 수 없습니다.");
    }

    @Test
    void 오너를_매니저로_강등할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        memberships.put(ownerId, ownerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.promoteToManager(ownerId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채널 오너를 매니저로 강등할 수 없습니다.");
    }

    @Test
    void 이미_매니저인_멤버를_승격할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId managerId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        ChannelMembership managerMembership = ChannelMembership.create(2L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.promoteToManager(ownerId, managerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 매니저입니다.");
    }

    @Test
    void 오너는_매니저를_멤버로_강등할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId managerId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        ChannelMembership managerMembership = ChannelMembership.create(2L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when
        ChannelMembership demotedMembership = channel.demoteToMember(ownerId, managerId);

        // then
        assertAll(
                () -> assertThat(demotedMembership).isNotNull(),
                () -> assertThat(demotedMembership.getRole()).isEqualTo(ChannelRole.MEMBER)
        );
    }

    @Test
    void 권한이_없는_사용자는_매니저를_강등할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        UserId managerId = UserId.create(3L);

        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        ChannelMembership managerMembership = ChannelMembership.create(3L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(), createdAt);

        memberships.put(memberId, memberMembership);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.demoteToMember(memberId, managerId))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("역할을 변경할 권한이 없습니다.");
    }

    @Test
    void 오너를_멤버로_강등할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        memberships.put(ownerId, ownerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.demoteToMember(ownerId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채널 오너를 멤버로 강등시킬 수 없습니다.");
    }

    @Test
    void 이미_멤버인_사용자를_강등할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId memberId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.demoteToMember(ownerId, memberId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 멤버입니다.");
    }

    @Test
    void 오너는_매니저에게_권한을_추가할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId managerId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        Set<ChannelPermissionType> managerPermissions = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelMembership managerMembership = ChannelMembership.create(2L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(managerPermissions), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when
        ChannelMembership updatedMembership = channel.addPermission(ownerId, managerId, ChannelPermissionType.MEMBER_KICK);

        // then
        assertAll(
                () -> assertThat(updatedMembership).isNotNull(),
                () -> assertThat(updatedMembership.hasPermission(ChannelPermissionType.MEMBER_KICK)).isTrue()
        );
    }

    @Test
    void 권한이_없는_사용자는_매니저에게_권한을_추가할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        UserId managerId = UserId.create(3L);

        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        ChannelMembership managerMembership = ChannelMembership.create(3L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(), createdAt);

        memberships.put(memberId, memberMembership);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.addPermission(memberId, managerId, ChannelPermissionType.MEMBER_KICK))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("매니저의 권한을 추가할 권한이 없습니다.");
    }

    @Test
    void 매니저가_아닌_멤버에게는_권한을_추가할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId memberId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.addPermission(ownerId, memberId, ChannelPermissionType.MEMBER_KICK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 멤버는 매니저가 아닙니다.");
    }

    @Test
    void 이미_가지고_있는_권한은_추가할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId managerId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        Set<ChannelPermissionType> managerPermissions = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT, ChannelPermissionType.MEMBER_KICK);
        ChannelMembership managerMembership = ChannelMembership.create(2L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(managerPermissions), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.addPermission(ownerId, managerId, ChannelPermissionType.MEMBER_KICK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 해당 권한을 가지고 있습니다.");
    }

    @Test
    void 오너는_매니저의_권한을_제거할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId managerId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        Set<ChannelPermissionType> managerPermissions = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT, ChannelPermissionType.MEMBER_KICK);
        ChannelMembership managerMembership = ChannelMembership.create(2L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(managerPermissions), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when
        ChannelMembership updatedMembership = channel.removePermission(ownerId, managerId, ChannelPermissionType.MEMBER_KICK);

        // then
        assertAll(
                () -> assertThat(updatedMembership).isNotNull(),
                () -> assertThat(updatedMembership.hasPermission(ChannelPermissionType.MEMBER_KICK)).isFalse()
        );
    }

    @Test
    void 권한이_없는_사용자는_매니저의_권한을_제거할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        UserId managerId = UserId.create(3L);

        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        ChannelMembership managerMembership = ChannelMembership.create(3L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(), createdAt);

        memberships.put(memberId, memberMembership);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.removePermission(memberId, managerId, ChannelPermissionType.MEMBER_KICK))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("매니저의 권한을 추가할 권한이 없습니다.");
    }

    @Test
    void 가지고_있지_않은_권한은_제거할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId managerId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        Set<ChannelPermissionType> managerPermissions = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelMembership managerMembership = ChannelMembership.create(2L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(managerPermissions), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.removePermission(ownerId, managerId, ChannelPermissionType.MEMBER_KICK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 권한을 가지고 있지 않습니다.");
    }

    @Test
    void 오너는_채널을_삭제할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        memberships.put(ownerId, ownerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.validateDeleteChannel(ownerId));
    }

    @Test
    void 오너가_아니면_채널을_삭제할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateDeleteChannel(memberId))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("채널을 삭제할 권한이 없습니다.");
    }

    @Test
    void 오너는_채널_이름을_변경할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        memberships.put(ownerId, ownerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);
        String newName = "new-channel";

        // when
        Channel editedChannel = channel.editName(ownerId, newName);

        // then
        assertThat(editedChannel.getName()).isEqualTo(newName);
    }

    @Test
    void 채널_이름_변경_권한이_있는_매니저는_채널_이름을_변경할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId managerId = UserId.create(2L);
        Set<ChannelPermissionType> managerPermissions = EnumSet.of(ChannelPermissionType.EDIT_CHANNEL_NAME);
        ChannelMembership managerMembership = ChannelMembership.create(2L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(managerPermissions), createdAt);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);
        String newName = "new-channel";

        // when
        Channel editedChannel = channel.editName(managerId, newName);

        // then
        assertThat(editedChannel.getName()).isEqualTo(newName);
    }

    @Test
    void 권한이_없는_사용자는_채널_이름을_변경할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.editName(memberId, "new-channel"))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("채널 이름을 변경할 권한이 없습니다.");
    }

    @Test
    void 오너는_채널_정책을_변경할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        memberships.put(ownerId, ownerMembership);

        ChannelPolicy oldPolicy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, oldPolicy, memberships, createdAt);

        ChannelPolicy newPolicy = ChannelPolicy.defaultPolicy().updatePublic(false);

        // when
        Channel updatedChannel = channel.changeChannelPolicy(ownerId, newPolicy);

        // then
        assertThat(updatedChannel.getChannelPolicy()).isEqualTo(newPolicy);
    }

    @Test
    void 권한이_없는_사용자는_채널_정책을_변경할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        ChannelPolicy newPolicy = ChannelPolicy.defaultPolicy().updatePublic(false);

        // when & then
        assertThatThrownBy(() -> channel.changeChannelPolicy(memberId, newPolicy))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("채널 정책을 변경할 권한이 없습니다.");
    }

    @Test
    void 공개_채널에는_자유롭게_참여할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        ChannelPolicy publicPolicy = ChannelPolicy.defaultPolicy().updatePublic(true);
        Channel channel = Channel.create("general", 1L, publicPolicy, createdAt);

        UserId newUserId = UserId.create(2L);
        LocalDateTime joinedAt = LocalDateTime.now();

        // when
        ChannelMembership joinerMembership = channel.joinUser(newUserId, ChannelRole.MEMBER, joinedAt);

        // then
        assertAll(
                () -> assertThat(joinerMembership).isNotNull(),
                () -> assertThat(joinerMembership.getUserId()).isEqualTo(newUserId),
                () -> assertThat(joinerMembership.getRole()).isEqualTo(ChannelRole.MEMBER)
        );
    }

    @Test
    void 비공개_채널에는_참여할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        ChannelPolicy privatePolicy = ChannelPolicy.defaultPolicy().updatePublic(false);
        Channel channel = Channel.create("private-channel", 1L, privatePolicy, createdAt);

        UserId newUserId = UserId.create(2L);
        LocalDateTime joinedAt = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> channel.joinUser(newUserId, ChannelRole.MEMBER, joinedAt))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("비공개 채널입니다. 초대를 통해 참여할 수 있습니다.");
    }

    @Test
    void 이미_참여한_사용자는_다시_참여할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId existingUserId = UserId.create(2L);
        ChannelMembership existingMembership = ChannelMembership.create(2L, 1L, existingUserId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(existingUserId, existingMembership);

        ChannelPolicy publicPolicy = ChannelPolicy.defaultPolicy().updatePublic(true);
        Channel channel = Channel.create(1L, "general", 1L, publicPolicy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.joinUser(existingUserId, ChannelRole.MEMBER, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 채널에 참여한 사용자입니다.");
    }

    @Test
    void 권한이_있는_멤버는_다른_사용자를_초대할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        memberships.put(ownerId, ownerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        UserId inviteeId = UserId.create(2L);
        LocalDateTime invitedAt = LocalDateTime.now();

        // when
        ChannelMembership inviteeMembership = channel.inviteMember(ownerId, inviteeId, invitedAt);

        // then
        assertAll(
                () -> assertThat(inviteeMembership).isNotNull(),
                () -> assertThat(inviteeMembership.getUserId()).isEqualTo(inviteeId),
                () -> assertThat(inviteeMembership.getRole()).isEqualTo(ChannelRole.MEMBER)
        );
    }

    @Test
    void 권한이_없는_사용자는_멤버를_초대할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        UserId inviteeId = UserId.create(3L);

        // when & then
        assertThatThrownBy(() -> channel.inviteMember(memberId, inviteeId, LocalDateTime.now()))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버 초대 권한이 없습니다.");
    }

    @Test
    void 이미_참여한_사용자는_초대할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId existingMemberId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        ChannelMembership existingMembership = ChannelMembership.create(2L, 1L, existingMemberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(existingMemberId, existingMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.inviteMember(ownerId, existingMemberId, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 해당 채널에 참여한 사용자입니다.");
    }

    @Test
    void 멤버는_채널에서_나갈_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when
        ChannelMembership leftMembership = channel.leaveMember(memberId);

        // then
        assertAll(
                () -> assertThat(leftMembership).isNotNull(),
                () -> assertThat(channel.getMemberships().containsKey(memberId)).isFalse()
        );
    }

    @Test
    void 오너는_채널에서_나갈_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        memberships.put(ownerId, ownerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.leaveMember(ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채널 오너는 채널에서 나갈 수 없습니다.");
    }

    @Test
    void 권한이_있는_사용자는_멤버를_강퇴할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId targetMemberId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        ChannelMembership targetMembership = ChannelMembership.create(2L, 1L, targetMemberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(targetMemberId, targetMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when
        ChannelMembership kickedMembership = channel.kickMember(ownerId, targetMemberId);

        // then
        assertAll(
                () -> assertThat(kickedMembership).isNotNull(),
                () -> assertThat(channel.getMemberships().containsKey(targetMemberId)).isFalse()
        );
    }

    @Test
    void 권한이_없는_사용자는_멤버를_강퇴할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        UserId targetMemberId = UserId.create(3L);

        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        ChannelMembership targetMembership = ChannelMembership.create(3L, 1L, targetMemberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);

        memberships.put(memberId, memberMembership);
        memberships.put(targetMemberId, targetMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.kickMember(memberId, targetMemberId))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버를 강퇴할 권한이 없습니다.");
    }

    @Test
    void 멤버가_아닌_사용자는_강퇴할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId ownerId = UserId.create(1L);
        UserId managerId = UserId.create(2L);

        ChannelMembership ownerMembership = ChannelMembership.create(1L, 1L, ownerId.getValue(), ChannelRole.OWNER, ChannelManagePermissions.ofOwner(), createdAt);
        ChannelMembership managerMembership = ChannelMembership.create(2L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(), createdAt);

        memberships.put(ownerId, ownerMembership);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.kickMember(ownerId, managerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("멤버만 강퇴할 수 있습니다.");
    }

    @Test
    void 자신의_메시지는_수정할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy().updateEditOwnMessage(true);
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        ChatMessage message = new ChatMessage(1L, 1L, memberId.getValue(), 1L, "test", createdAt, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.validateMemberEditMessage(memberId, message));
    }

    @Test
    void 권한이_있는_매니저는_다른_사용자의_메시지를_수정할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId managerId = UserId.create(2L);
        Set<ChannelPermissionType> managerPermissions = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelMembership managerMembership = ChannelMembership.create(2L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(managerPermissions), createdAt);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        ChatMessage message = new ChatMessage(1L, 1L, 999L, 1L, "test", createdAt, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.validateMemberEditMessage(managerId, message));
    }

    @Test
    void 권한이_없는_사용자는_다른_사용자의_메시지를_수정할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        ChatMessage message = new ChatMessage(1L, 1L, 999L, 1L, "test", createdAt, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateMemberEditMessage(memberId, message))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("메시지를 수정할 권한이 없습니다.");
    }

    @Test
    void 채널_정책이_허용하지_않으면_자신의_메시지도_수정할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy().updateEditOwnMessage(false);
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        ChatMessage message = new ChatMessage(1L, 1L, memberId.getValue(), 1L, "test", createdAt, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateMemberEditMessage(memberId, message))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("자신의 메시지를 수정할 수 없습니다.");
    }

    @Test
    void 자신의_메시지는_삭제할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy().updateDeleteOwnMessage(true);
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        ChatMessage message = new ChatMessage(1L, 1L, memberId.getValue(), 1L, "test", createdAt, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.validateMemberDeleteMessage(memberId, message));
    }

    @Test
    void 권한이_있는_매니저는_다른_사용자의_메시지를_삭제할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId managerId = UserId.create(2L);
        Set<ChannelPermissionType> managerPermissions = EnumSet.of(ChannelPermissionType.MESSAGE_DELETE);
        ChannelMembership managerMembership = ChannelMembership.create(2L, 1L, managerId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(managerPermissions), createdAt);
        memberships.put(managerId, managerMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        ChatMessage message = new ChatMessage(1L, 1L, 999L, 1L, "test", createdAt, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.validateMemberDeleteMessage(managerId, message));
    }

    @Test
    void 권한이_없는_사용자는_다른_사용자의_메시지를_삭제할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        ChatMessage message = new ChatMessage(1L, 1L, 999L, 1L, "test", createdAt, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateMemberDeleteMessage(memberId, message))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("메시지를 삭제할 권한이 없습니다.");
    }

    @Test
    void 채널_정책이_허용하지_않으면_자신의_메시지도_삭제할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy().updateDeleteOwnMessage(false);
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        ChatMessage message = new ChatMessage(1L, 1L, memberId.getValue(), 1L, "test", createdAt, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateMemberDeleteMessage(memberId, message))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("자신의 메시지를 삭제할 수 없습니다.");
    }

    @Test
    void 채널에_참여한_멤버는_메시지를_전송할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership memberMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, memberMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.validateMemberSendMessage(memberId));
    }

    @Test
    void 채널에_참여하지_않은_사용자는_메시지를_전송할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create("general", 1L, policy, createdAt);

        UserId nonMemberId = UserId.create(999L);

        // when & then
        assertThatThrownBy(() -> channel.validateMemberSendMessage(nonMemberId))
                .isInstanceOf(ChannelMembershipNotFoundException.class)
                .hasMessage("채널 멤버를 찾을 수 없습니다.");
    }

    @Test
    void 멤버십을_동기화할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();

        UserId memberId = UserId.create(2L);
        ChannelMembership oldMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MEMBER, ChannelManagePermissions.ofMember(), createdAt);
        memberships.put(memberId, oldMembership);

        ChannelPolicy policy = ChannelPolicy.defaultPolicy();
        Channel channel = Channel.create(1L, "general", 1L, policy, memberships, createdAt);

        ChannelMembership newMembership = ChannelMembership.create(2L, 1L, memberId.getValue(), ChannelRole.MANAGER, ChannelManagePermissions.ofManager(), createdAt);

        // when
        channel.syncMembership(newMembership);

        // then
        assertThat(channel.getMemberships()).containsEntry(memberId, newMembership);
    }
}
