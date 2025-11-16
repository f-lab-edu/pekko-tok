package com.tok.pekko.domain.channel.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.tok.pekko.domain.channel.model.Channel.ChannelOperationForbiddenException;
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
    void 채널_ID를_할당할_수_있다() {
        // given
        Channel channel = Channel.create("general", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        Long assignedId = 10L;

        // when
        Channel assignedChannel = channel.withAssignedId(assignedId);

        // then
        assertAll(
                () -> assertThat(assignedChannel.getChannelId()).isEqualTo(ChannelId.create(assignedId)),
                () -> assertThat(assignedChannel.getName()).isEqualTo(channel.getName()),
                () -> assertThat(assignedChannel.getCreatorId()).isEqualTo(channel.getCreatorId())
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

        // when & then
        assertDoesNotThrow(() -> channel.validateJoinMember(userId));
    }

    @Test
    void 비공개_채널에는_직접_참여할_수_없다() {
        // given
        ChannelPolicy privatePolicy = new ChannelPolicy(true, true, false);
        Channel channel = Channel.create("private", 1L, privatePolicy, LocalDateTime.now());
        UserId userId = UserId.create(2L);

        // when & then
        assertThatThrownBy(() -> channel.validateJoinMember(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비공개 채널입니다. 초대를 통해 참여할 수 있습니다.");
    }

    @Test
    void 이미_참여한_멤버는_다시_참여할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId userId = UserId.create(2L);
        memberships.put(userId, ChannelMembership.create(ChannelId.create(1L), userId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateJoinMember(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 채널에 참여한 사용자입니다.");
    }

    @Test
    void 초대_권한이_있는_사용자는_멤버를_초대할_수_있다() {
        // given
        ChannelPolicy privatePolicy = new ChannelPolicy(true, true, false);
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId inviterId = UserId.create(2L);
        memberships.put(inviterId, ChannelMembership.create(ChannelId.create(1L), inviterId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "private", 1L, privatePolicy, memberships, createdAt);
        UserId inviteeId = UserId.create(3L);

        // when & then
        assertDoesNotThrow(() -> channel.validateInviteMember(inviterId, inviteeId));
    }

    @Test
    void 초대_권한이_없는_사용자는_멤버를_초대할_수_없다() {
        // given
        ChannelPolicy privatePolicy = new ChannelPolicy(true, true, false);
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId inviterId = UserId.create(2L);
        memberships.put(inviterId, ChannelMembership.create(ChannelId.create(1L), inviterId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "private", 1L, privatePolicy, memberships, createdAt);
        UserId inviteeId = UserId.create(3L);

        // when & then
        assertThatThrownBy(() -> channel.validateInviteMember(inviterId, inviteeId))
                .isInstanceOf(Channel.ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버를 초대할 권한이 없습니다.");
    }

    @Test
    void 초대된_멤버도_중복으로_초대할_수_없다() {
        // given
        ChannelPolicy privatePolicy = new ChannelPolicy(true, true, false);
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId inviterId = UserId.create(2L);
        UserId inviteeId = UserId.create(3L);
        memberships.put(inviterId, ChannelMembership.create(ChannelId.create(1L), inviterId, ChannelRole.OWNER, createdAt));
        memberships.put(inviteeId, ChannelMembership.create(ChannelId.create(1L), inviteeId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "private", 1L, privatePolicy, memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateInviteMember(inviterId, inviteeId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 채널에 참여한 사용자입니다.");
    }

    @Test
    void 오너는_멤버를_매니저로_승격할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.OWNER, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when
        ChannelMembership promoted = channel.promoteToManager(executorId, targetUserId);

        // then
        assertThat(promoted.isManager()).isTrue();
    }

    @Test
    void 권한이_없는_사용자는_멤버를_승격할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.MEMBER, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.promoteToManager(executorId, targetUserId))
                .isInstanceOf(Channel.ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버의 역할을 변경할 권한이 없습니다.");
    }

    @Test
    void 존재하지_않는_멤버를_승격할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.promoteToManager(executorId, targetUserId))
                .isInstanceOf(Channel.ChannelMembershipNotFoundException.class)
                .hasMessage("채널 멤버를 찾을 수 없습니다.");
    }

    @Test
    void 이미_매니저인_멤버는_승격할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.OWNER, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.MANAGER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.promoteToManager(executorId, targetUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 매니저입니다.");
    }

    @Test
    void 오너를_매니저로_강등할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.OWNER, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.promoteToManager(executorId, targetUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오너를 매니저로 강등시킬 수 없습니다.");
    }

    @Test
    void 오너는_매니저를_멤버로_강등할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.OWNER, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.MANAGER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when
        ChannelMembership demoted = channel.demoteToMember(executorId, targetUserId);

        // then
        assertThat(demoted.isMember()).isTrue();
    }

    @Test
    void 권한이_없는_사용자는_멤버를_강등할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.MEMBER, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.MANAGER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.demoteToMember(executorId, targetUserId))
                .isInstanceOf(Channel.ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버의 역할을 변경할 권한이 없습니다.");
    }

    @Test
    void 이미_멤버인_경우_강등할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.OWNER, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.demoteToMember(executorId, targetUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 멤버입니다.");
    }

    @Test
    void 오너를_멤버로_강등할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.OWNER, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.demoteToMember(executorId, targetUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오너를 멤버로 강등시킬 수 없습니다.");
    }

    @Test
    void 존재하지_않는_멤버를_강등할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.demoteToMember(executorId, targetUserId))
                .isInstanceOf(Channel.ChannelMembershipNotFoundException.class)
                .hasMessage("채널 멤버를 찾을 수 없습니다.");
    }

    @Test
    void 오너는_매니저에게_권한을_추가할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId grantorId = UserId.create(2L);
        UserId granteeId = UserId.create(3L);
        memberships.put(grantorId, ChannelMembership.create(ChannelId.create(1L), grantorId, ChannelRole.OWNER, createdAt));
        memberships.put(granteeId, ChannelMembership.create(ChannelId.create(1L), granteeId, ChannelRole.MANAGER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.getValidatedAddTarget(grantorId, granteeId, ChannelPermissionType.MEMBER_KICK));
    }

    @Test
    void 권한이_없는_사용자는_매니저에게_권한을_추가할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId grantorId = UserId.create(2L);
        UserId granteeId = UserId.create(3L);
        memberships.put(grantorId, ChannelMembership.create(ChannelId.create(1L), grantorId, ChannelRole.MEMBER, createdAt));
        memberships.put(granteeId, ChannelMembership.create(ChannelId.create(1L), granteeId, ChannelRole.MANAGER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.getValidatedAddTarget(grantorId, granteeId, ChannelPermissionType.MESSAGE_EDIT))
                .isInstanceOf(Channel.ChannelMembershipOperationForbiddenException.class)
                .hasMessage("매니저의 권한을 추가할 권한이 없습니다.");
    }

    @Test
    void 멤버에게_권한을_추가할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId grantorId = UserId.create(2L);
        UserId granteeId = UserId.create(3L);
        memberships.put(grantorId, ChannelMembership.create(ChannelId.create(1L), grantorId, ChannelRole.OWNER, createdAt));
        memberships.put(granteeId, ChannelMembership.create(ChannelId.create(1L), granteeId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.getValidatedAddTarget(grantorId, granteeId, ChannelPermissionType.MESSAGE_EDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 멤버는 매니저가 아닙니다.");
    }

    @Test
    void 이미_가지고_있는_권한은_추가할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId grantorId = UserId.create(2L);
        UserId granteeId = UserId.create(3L);
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MEMBER_KICK);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        memberships.put(grantorId, ChannelMembership.create(ChannelId.create(1L), grantorId, ChannelRole.OWNER, createdAt));
        memberships.put(granteeId, ChannelMembership.create(1L, 1L, granteeId.getValue(), ChannelRole.MANAGER, permissions, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.getValidatedAddTarget(grantorId, granteeId, ChannelPermissionType.MEMBER_KICK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 해당 권한을 가지고 있습니다.");
    }

    @Test
    void 오너는_매니저에게서_권한을_제거할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId grantorId = UserId.create(2L);
        UserId granteeId = UserId.create(3L);
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        memberships.put(grantorId, ChannelMembership.create(ChannelId.create(1L), grantorId, ChannelRole.OWNER, createdAt));
        memberships.put(granteeId, ChannelMembership.create(1L, 1L, granteeId.getValue(), ChannelRole.MANAGER, permissions, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.getValidatedRemoveTarget(grantorId, granteeId, ChannelPermissionType.MESSAGE_EDIT));
    }

    @Test
    void 권한이_없는_사용자는_매니저에게서_권한을_제거할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId grantorId = UserId.create(2L);
        UserId granteeId = UserId.create(3L);
        Set<ChannelPermissionType> perms = EnumSet.of(ChannelPermissionType.MESSAGE_EDIT);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(perms);
        memberships.put(grantorId, ChannelMembership.create(ChannelId.create(1L), grantorId, ChannelRole.MEMBER, createdAt));
        memberships.put(granteeId, ChannelMembership.create(1L, 1L, granteeId.getValue(), ChannelRole.MANAGER, permissions, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.getValidatedRemoveTarget(grantorId, granteeId, ChannelPermissionType.MESSAGE_EDIT))
                .isInstanceOf(Channel.ChannelMembershipOperationForbiddenException.class)
                .hasMessage("매니저의 권한을 추가할 권한이 없습니다.");
    }

    @Test
    void 가지고_있지_않은_권한은_제거할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId grantorId = UserId.create(2L);
        UserId granteeId = UserId.create(3L);
        memberships.put(grantorId, ChannelMembership.create(ChannelId.create(1L), grantorId, ChannelRole.OWNER, createdAt));

        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager();
        memberships.put(granteeId, ChannelMembership.create(1L, 1L, granteeId.getValue(), ChannelRole.MANAGER, permissions, createdAt));

        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.getValidatedRemoveTarget(grantorId, granteeId, ChannelPermissionType.MEMBER_INVITE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 권한을 가지고 있지 않습니다.");
    }

    @Test
    void 멤버를_채널에서_제거할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId userId = UserId.create(2L);
        memberships.put(userId, ChannelMembership.create(ChannelId.create(1L), userId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.validateLeaveMember(userId));
    }

    @Test
    void 오너는_채널에서_나갈_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId userId = UserId.create(2L);
        memberships.put(userId, ChannelMembership.create(ChannelId.create(1L), userId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateLeaveMember(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오너는 채널에서 나갈 수 없습니다.");
    }

    @Test
    void 오너는_채널을_삭제할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId userId = UserId.create(2L);
        memberships.put(userId, ChannelMembership.create(ChannelId.create(1L), userId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.validateDeleteChannel(userId));
    }

    @Test
    void 오너가_아니면_채널을_삭제할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId userId = UserId.create(2L);
        memberships.put(userId, ChannelMembership.create(ChannelId.create(1L), userId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateDeleteChannel(userId))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("채널을 삭제할 권한이 없습니다.");
    }

    @Test
    void 오너가_채널_이름을_변경한다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId changerId = UserId.create(2L);
        memberships.put(changerId, ChannelMembership.create(ChannelId.create(1L), changerId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);
        String newName = "random";

        // when
        Channel changedChannel = channel.changeName(changerId, newName);

        // then
        assertAll(
                () -> assertThat(changedChannel.getName()).isEqualTo(newName),
                () -> assertThat(changedChannel.getCreatorId()).isEqualTo(channel.getCreatorId()),
                () -> assertThat(changedChannel.getChannelPolicy()).isEqualTo(channel.getChannelPolicy())
        );
    }

    @Test
    void 오너가_아니면_채널_이름을_변경할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId changerId = UserId.create(2L);
        memberships.put(changerId, ChannelMembership.create(ChannelId.create(1L), changerId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);
        String newName = "random";

        // when & then
        assertThatThrownBy(() -> channel.changeName(changerId, newName))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("채널 이름을 변경할 권한이 없습니다.");
    }

    @Test
    void 채널_이름_변경_시_유효하지_않은_이름은_거부된다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId changerId = UserId.create(2L);
        memberships.put(changerId, ChannelMembership.create(ChannelId.create(1L), changerId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.changeName(changerId, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채널 이름은 필수입니다.");
    }

    @Test
    void 권한이_있는_사용자가_채널_정책을_변경한다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId changerId = UserId.create(2L);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofOwner();
        memberships.put(changerId, ChannelMembership.create(1L, 1L, changerId.getValue(), ChannelRole.OWNER, permissions, createdAt)); // OWNER 역할로 변경
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);
        ChannelPolicy newPolicy = new ChannelPolicy(false, false, false);

        // when
        Channel actual = channel.changeChannelPolicy(changerId, newPolicy);

        // then
        assertThat(actual.getChannelPolicy()).isEqualTo(newPolicy);
    }

    @Test
    void 권한이_없으면_채널_정책을_변경할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        UserId changerId = UserId.create(2L);
        memberships.put(changerId, ChannelMembership.create(ChannelId.create(1L), changerId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);
        ChannelPolicy newPolicy = new ChannelPolicy(false, false, false);

        // when & then
        assertThatThrownBy(() -> channel.changeChannelPolicy(changerId, newPolicy))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("채널 정책을 변경할 권한이 없습니다.");
    }

    @Test
    void 강퇴_권한이_없는_사용자는_멤버를_강퇴할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.MEMBER, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.MEMBER, createdAt));

        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateKickMember(executorId, targetUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("멤버를 강퇴할 권한이 없습니다.");
    }

    @Test
    void 존재하지_않는_사용자는_강퇴할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        // executor는 존재하지만 타겟이 없음
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.MANAGER, createdAt));

        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateKickMember(executorId, targetUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("멤버를 강퇴할 권한이 없습니다.");
    }

    @Test
    void 자기자신은_강퇴할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        UserId executorId = UserId.create(2L);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateKickMember(executorId, executorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("자기 자신을 강퇴할 수 없습니다.");
    }

    @Test
    void 오너는_강퇴할_수_없다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(executorId, ChannelMembership.create(ChannelId.create(1L), executorId, ChannelRole.OWNER, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.OWNER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertThatThrownBy(() -> channel.validateKickMember(executorId, targetUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오너를 강퇴할 수 없습니다.");
    }

    @Test
    void 강퇴_권한이_있는_매니저는_멤버를_강퇴할_수_있다() {
        // given
        LocalDateTime createdAt = LocalDateTime.now();
        UserId executorId = UserId.create(2L);
        UserId targetUserId = UserId.create(3L);
        ChannelManagePermissions permissions = ChannelManagePermissions.ofManager(EnumSet.of(ChannelPermissionType.MEMBER_KICK));
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(executorId, ChannelMembership.create(1L, 1L, 2L, ChannelRole.MANAGER, permissions, createdAt));
        memberships.put(targetUserId, ChannelMembership.create(ChannelId.create(1L), targetUserId, ChannelRole.MEMBER, createdAt));
        Channel channel = Channel.create(1L, "general", 1L, ChannelPolicy.defaultPolicy(), memberships, createdAt);

        // when & then
        assertDoesNotThrow(() -> channel.validateKickMember(executorId, targetUserId));
    }
}
