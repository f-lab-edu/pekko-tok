package com.tok.pekko.application.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelMembershipServiceTest {

    @Test
    void 멤버가_채널에_참여한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.fixed(Instant.parse("2024-03-01T12:30:00Z"), ZoneOffset.UTC);
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        Channel channel = Channel.create(
                1L,
                "general",
                1L,
                ChannelPolicy.defaultPolicy(),
                new HashMap<>(),
                LocalDateTime.now(clock)
        );
        when(channelStoragePort.findChannel(1L)).thenReturn(channel);

        // when
        service.joinChannel(1L, 10L);

        // then
        assertAll(
                () -> assertThat(channel.getMemberships()).hasSize(1),
                () -> assertThat(channel.getMemberships()).containsKey(UserId.create(10L))
        );
    }

    @Test
    void 초대_권한이_없는_사용자는_멤버를_초대할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(30L),
                ChannelMembership.create(UserId.create(30L), ChannelRole.MEMBER, createdAt)
        );
        Channel channel = Channel.create(
                2L,
                "channel-2",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(2L)).thenReturn(channel);

        // when & then
        assertThatThrownBy(() -> service.inviteMember(2L, 30L, 40L))
                .isInstanceOf(ChannelMembershipService.ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버를 초대할 권한이 없습니다.");

        assertThat(channel.getMemberships()).hasSize(1);
    }

    @Test
    void 오너는_멤버를_초대할_수_있다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(30L),
                ChannelMembership.create(UserId.create(30L), ChannelRole.OWNER, createdAt)
        );
        Channel channel = Channel.create(
                2L,
                "channel-2",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(2L)).thenReturn(channel);

        // when
        service.inviteMember(2L, 30L, 40L);

        // then
        assertAll(
                () -> assertThat(channel.getMemberships()).hasSize(2),
                () -> assertThat(channel.getMemberships()).containsKey(UserId.create(40L))
        );
    }

    @Test
    void 초대_권한이_있는_매니저는_멤버를_초대할_수_있다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        ChannelMembership manager = ChannelMembership.create(
                1L,
                30L,
                ChannelRole.MANAGER,
                ChannelManagePermissions.ofManager(EnumSet.of(ChannelPermissionType.MEMBER_INVITE)),
                createdAt
        );
        memberships.put(UserId.create(30L), manager);
        Channel channel = Channel.create(
                2L,
                "channel-2",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(2L)).thenReturn(channel);

        // when
        service.inviteMember(2L, 30L, 40L);

        // then
        assertAll(
                () -> assertThat(channel.getMemberships()).hasSize(2),
                () -> assertThat(channel.getMemberships()).containsKey(UserId.create(40L))
        );
    }

    @Test
    void 멤버가_채널을_탈퇴한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(50L),
                ChannelMembership.create(UserId.create(50L), ChannelRole.MEMBER, createdAt)
        );
        Channel channel = Channel.create(
                3L,
                "channel-3",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(3L)).thenReturn(channel);

        // when
        service.leaveChannel(3L, 50L);

        // then
        assertThat(channel.getMemberships()).isEmpty();
    }

    @Test
    void 권한이_없으면_멤버_역할을_관리할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(60L),
                ChannelMembership.create(UserId.create(60L), ChannelRole.MANAGER, createdAt)
        );
        memberships.put(
                UserId.create(70L),
                ChannelMembership.create(UserId.create(70L), ChannelRole.MANAGER, createdAt)
        );
        Channel channel = Channel.create(
                4L,
                "channel-4",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(4L)).thenReturn(channel);

        // when & then
        assertThatThrownBy(() -> service.managedMemberRole(4L, 60L, 70L, ChannelRole.MEMBER))
                .isInstanceOf(ChannelMembershipService.ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버의 역할을 변경할 권한이 없습니다.");
    }

    @Test
    void 오너가_멤버를_강등한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(60L),
                ChannelMembership.create(UserId.create(60L), ChannelRole.OWNER, createdAt)
        );
        memberships.put(
                UserId.create(70L),
                ChannelMembership.create(UserId.create(70L), ChannelRole.MANAGER, createdAt)
        );
        Channel channel = Channel.create(
                4L,
                "channel-4",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(4L)).thenReturn(channel);

        // when & then
        assertThatThrownBy(() -> service.managedMemberRole(4L, 60L, 70L, ChannelRole.MEMBER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오너 역할은 부여할 수 없습니다.");

        ChannelMembership updated = channel.getMemberships().get(UserId.create(70L));
        assertAll(
                () -> assertThat(updated.isMember()).isTrue(),
                () -> assertThat(updated.isManager()).isFalse()
        );
    }

    @Test
    void 오너가_멤버를_승격한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(60L),
                ChannelMembership.create(UserId.create(60L), ChannelRole.OWNER, createdAt)
        );
        memberships.put(
                UserId.create(80L),
                ChannelMembership.create(UserId.create(80L), ChannelRole.MEMBER, createdAt)
        );
        Channel channel = Channel.create(
                4L,
                "channel-4",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(4L)).thenReturn(channel);

        // when & then
        assertThatThrownBy(() -> service.managedMemberRole(4L, 60L, 80L, ChannelRole.MANAGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오너 역할은 부여할 수 없습니다.");

        ChannelMembership updated = channel.getMemberships().get(UserId.create(80L));
        assertAll(
                () -> assertThat(updated.isManager()).isTrue(),
                () -> assertThat(updated.isMember()).isFalse()
        );
    }

    @Test
    void 권한이_없으면_멤버_권한을_추가할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(90L),
                ChannelMembership.create(UserId.create(90L), ChannelRole.MANAGER, createdAt)
        );
        memberships.put(
                UserId.create(91L),
                ChannelMembership.create(UserId.create(91L), ChannelRole.MANAGER, createdAt)
        );
        Channel channel = Channel.create(
                5L,
                "channel-5",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(5L)).thenReturn(channel);

        // when & then
        assertThatThrownBy(() -> service.addPermissions(5L, 90L, 91L, ChannelPermissionType.MESSAGE_EDIT))
                .isInstanceOf(ChannelMembershipService.ChannelMembershipOperationForbiddenException.class)
                .hasMessage("채널 권한을 추가할 권한이 없습니다.");
    }

    @Test
    void 오너는_멤버_권한을_추가할_수_있다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(90L),
                ChannelMembership.create(UserId.create(90L), ChannelRole.OWNER, createdAt)
        );
        memberships.put(
                UserId.create(91L),
                ChannelMembership.create(UserId.create(91L), ChannelRole.MANAGER, createdAt)
        );
        Channel channel = Channel.create(
                5L,
                "channel-5",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(5L)).thenReturn(channel);

        // when
        service.addPermissions(5L, 90L, 91L, ChannelPermissionType.MESSAGE_DELETE);

        // then
        ChannelMembership grantee = channel.getMemberships().get(UserId.create(91L));
        assertThat(grantee.getPermissions().has(ChannelPermissionType.MESSAGE_DELETE)).isTrue();
    }

    @Test
    void 권한이_없으면_멤버_권한을_삭제할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(92L),
                ChannelMembership.create(UserId.create(92L), ChannelRole.MANAGER, createdAt)
        );
        memberships.put(
                UserId.create(93L),
                ChannelMembership.create(
                        1L,
                        93L,
                        ChannelRole.MANAGER,
                        ChannelManagePermissions.ofManager(EnumSet.of(ChannelPermissionType.MEMBER_KICK)),
                        createdAt
                )
        );
        Channel channel = Channel.create(
                6L,
                "channel-6",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(6L)).thenReturn(channel);

        // when & then
        assertThatThrownBy(() -> service.removePermissions(6L, 92L, 93L, ChannelPermissionType.MEMBER_KICK))
                .isInstanceOf(ChannelMembershipService.ChannelMembershipOperationForbiddenException.class)
                .hasMessage("채널 권한을 삭제할 권한이 없습니다.");

        ChannelMembership grantee = channel.getMemberships().get(UserId.create(93L));
        assertThat(grantee.getPermissions().has(ChannelPermissionType.MEMBER_KICK)).isTrue();
    }

    @Test
    void 오너는_멤버_권한을_삭제할_수_있다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(clock, channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(92L),
                ChannelMembership.create(UserId.create(92L), ChannelRole.OWNER, createdAt)
        );
        memberships.put(
                UserId.create(93L),
                ChannelMembership.create(
                        1L,
                        93L,
                        ChannelRole.MANAGER,
                        ChannelManagePermissions.ofManager(EnumSet.of(ChannelPermissionType.MEMBER_KICK)),
                        createdAt
                )
        );
        Channel channel = Channel.create(
                6L,
                "channel-6",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(6L)).thenReturn(channel);

        // when
        service.removePermissions(6L, 92L, 93L, ChannelPermissionType.MEMBER_KICK);

        // then
        ChannelMembership grantee = channel.getMemberships().get(UserId.create(93L));
        assertThat(grantee.getPermissions().has(ChannelPermissionType.MEMBER_KICK)).isFalse();
    }
}
