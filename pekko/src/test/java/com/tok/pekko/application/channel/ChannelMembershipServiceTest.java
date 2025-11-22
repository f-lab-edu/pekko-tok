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
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.channel.port.out.ChannelMembershipStoragePort;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionActorManagementService
        );
        Channel channel = Channel.create(
                "general",
                1L,
                ChannelPolicy.defaultPolicy(),
                LocalDateTime.now(clock)
        );
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        service.joinChannel(1L, 10L);

        // then
        verify(channelMembershipStoragePort, times(1)).joinChannel(eq(channel.getChannelId()), any(ChannelMembership.class));
        verify(clientSessionActorManagementService, times(1)).syncJoinChannel(1L, 10L);
    }

    @Test
    void 초대_권한이_없는_사용자는_멤버를_초대할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                channelStoragePort,
                channelMembershipStoragePort,
                clientSessionActorManagementService
        );
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(30L),
                ChannelMembership.create(ChannelId.create(2L), UserId.create(30L), ChannelRole.MEMBER, LocalDateTime.now(clock))
        );
        Channel channel = Channel.create(
                2L,
                "channel-2",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                LocalDateTime.now(clock)
        );
        when(channelStoragePort.findChannel(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when & then
        assertThatThrownBy(() -> service.inviteMember(2L, 30L, 40L))
                .isInstanceOf(ChannelMembershipOperationForbiddenException.class)
                .hasMessage("멤버 초대 권한이 없습니다.");
    }

    @Test
    void 오너는_멤버를_초대할_수_있다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ChannelMembershipStoragePort channelMembershipStoragePort = mock(ChannelMembershipStoragePort.class);
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        Clock clock = Clock.systemUTC();
        ChannelMembershipService service = new ChannelMembershipService(
                clock, channelStoragePort, channelMembershipStoragePort, clientSessionActorManagementService);

        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(30L),
                ChannelMembership.create(ChannelId.create(2L), UserId.create(30L), ChannelRole.OWNER, LocalDateTime.now(clock))
        );
        Channel channel = Channel.create(
                2L,
                "channel-2",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                LocalDateTime.now(clock)
        );
        when(channelStoragePort.findChannel(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        service.inviteMember(2L, 30L, 40L);

        // then
        verify(channelMembershipStoragePort, times(1)).joinChannel(eq(channel.getChannelId()), any(ChannelMembership.class));
        verify(clientSessionActorManagementService, times(1)).syncJoinChannel(2L, 40L);
    }
}
