package com.tok.pekko.application.channel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.Channel.ChannelMembershipOperationForbiddenException;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.channel.port.out.ChannelMembershipStoragePort;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.fixed(Instant.parse("2024-03-01T12:30:00Z"), ZoneOffset.UTC);
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
        Channel channel = Channel.create(
                1L,
                "general",
                1L,
                ChannelPolicy.defaultPolicy(),
                new HashMap<>(),
                LocalDateTime.now(clock)
        );
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        service.joinChannel(1L, 10L);

        // then
        verify(channelMembershipStoragePort, times(1)).joinChannel(eq(channel.getChannelId()), any(ChannelMembership.class));
        verify(clientSessionService, times(1)).syncJoinChannel(1L, 10L);
    }

    @Test
    void 초대_권한이_없는_사용자는_멤버를_초대할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when & then
        assertThatThrownBy(() -> service.inviteMember(2L, 30L, 40L))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버를 초대할 권한이 없습니다.");
    }

    @Test
    void 오너는_멤버를_초대할_수_있다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        service.inviteMember(2L, 30L, 40L);

        // then
        verify(channelMembershipStoragePort, times(1)).joinChannel(eq(channel.getChannelId()), any(ChannelMembership.class));
        verify(clientSessionService, times(1)).syncJoinChannel(2L, 40L);
    }

    @Test
    void 초대_권한이_있는_매니저는_멤버를_초대할_수_있다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        service.inviteMember(2L, 30L, 40L);

        // then
        verify(channelMembershipStoragePort, times(1)).joinChannel(eq(channel.getChannelId()), any(ChannelMembership.class));
        verify(clientSessionService, times(1)).syncJoinChannel(2L, 40L);
    }

    @Test
    void 멤버가_채널을_탈퇴한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        service.leaveChannel(3L, 50L);

        // then
        verify(channelMembershipStoragePort, times(1)).leaveChannel(eq(channel.getChannelId()), eq(UserId.create(50L)));
        verify(clientSessionService, times(1)).syncLeaveChannel(3L, 50L);
    }

    @Test
    void 권한이_없으면_멤버를_승격할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(60L),
                ChannelMembership.create(UserId.create(60L), ChannelRole.MANAGER, createdAt)
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
        when(channelStoragePort.findChannel(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when & then
        assertThatThrownBy(() -> service.promoteToManager(4L, 60L, 80L))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버의 역할을 변경할 권한이 없습니다.");
    }

    @Test
    void 오너는_멤버를_승격한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        service.promoteToManager(4L, 60L, 80L);

        // then
        verify(channelMembershipStoragePort, times(1)).promoteToManager(any(ChannelMembership.class));
    }

    @Test
    void 권한이_없으면_멤버를_강등할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when & then
        assertThatThrownBy(() -> service.demoteToMember(4L, 60L, 70L))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버의 역할을 변경할 권한이 없습니다.");
    }

    @Test
    void 오너가_매니저를_강등한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        service.demoteToMember(4L, 60L, 70L);

        // then
        verify(channelMembershipStoragePort, times(1)).demoteToMember(any(ChannelMembership.class));
    }

    @Test
    void 권한이_없으면_멤버_권한을_추가할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when & then
        assertThatThrownBy(() -> service.addPermission(5L, 90L, 91L, ChannelPermissionType.MESSAGE_EDIT))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("매니저의 권한을 추가할 권한이 없습니다.");
    }

    @Test
    void 오너는_멤버_권한을_추가할_수_있다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        service.addPermission(5L, 90L, 91L, ChannelPermissionType.MESSAGE_DELETE);

        // then
        verify(channelMembershipStoragePort, times(1)).addPermission(
                eq(UserId.create(91L)),
                eq(ChannelPermissionType.MESSAGE_DELETE)
        );
    }

    @Test
    void 권한이_없으면_멤버_권한을_삭제할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when & then
        assertThatThrownBy(() -> service.removePermission(6L, 92L, 93L, ChannelPermissionType.MEMBER_KICK))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("매니저의 권한을 추가할 권한이 없습니다.");
    }

    @Test
    void 오너는_멤버_권한을_삭제할_수_있다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionService
        );
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
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        service.removePermission(6L, 92L, 93L, ChannelPermissionType.MEMBER_KICK);

        // then
        verify(channelMembershipStoragePort, times(1)).removePermission(
                eq(UserId.create(93L)),
                eq(ChannelPermissionType.MEMBER_KICK)
        );
    }
}
