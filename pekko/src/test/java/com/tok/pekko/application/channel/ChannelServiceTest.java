package com.tok.pekko.application.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.Channel.ChannelOperationForbiddenException;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
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
import org.mockito.ArgumentCaptor;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelServiceTest {

    @Test
    void 채널을_생성하면_식별자를_반환한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        ChannelService channelService = new ChannelService(channelStoragePort);
        Channel storedChannel = Channel.create(
                99L,
                "general",
                1L,
                ChannelPolicy.defaultPolicy(),
                new HashMap<>(),
                LocalDateTime.now(clock)
        );
        when(channelStoragePort.store(any(Channel.class))).thenReturn(storedChannel);

        // when
        ChannelId actual = channelService.createChannel("general", 1L);

        // then
        assertThat(actual).isEqualTo(storedChannel.getChannelId());

        ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelStoragePort).store(channelCaptor.capture());
        Channel savedChannel = channelCaptor.getValue();

        assertAll(
                () -> assertThat(savedChannel.getName()).isEqualTo("general"),
                () -> assertThat(savedChannel.getCreatorId()).isEqualTo(UserId.create(1L)),
                () -> assertThat(savedChannel.getChannelPolicy()).isEqualTo(ChannelPolicy.defaultPolicy())
        );
    }

    @Test
    void 권한이_없으면_채널을_삭제할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelService channelService = new ChannelService(channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(20L),
                ChannelMembership.create(UserId.create(20L), ChannelRole.MEMBER, createdAt)
        );
        Channel channel = Channel.create(
                10L,
                "channel-10",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when & then
        assertThatThrownBy(() -> channelService.deleteChannel(10L, 20L))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("채널을 삭제할 권한이 없습니다.");

        verify(channelStoragePort, never()).delete(any(ChannelId.class));
    }

    @Test
    void 권한이_있으면_채널을_삭제한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelService channelService = new ChannelService(channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(20L),
                ChannelMembership.create(UserId.create(20L), ChannelRole.OWNER, createdAt)
        );
        Channel channel = Channel.create(
                10L,
                "channel-10",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        channelService.deleteChannel(10L, 20L);

        // then
        verify(channelStoragePort).delete(channel.getChannelId());
    }

    @Test
    void 권한이_없으면_채널_정책을_변경할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelService channelService = new ChannelService(channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(30L),
                ChannelMembership.create(UserId.create(30L), ChannelRole.MEMBER, createdAt)
        );
        Channel channel = Channel.create(
                10L,
                "channel-10",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when & then
        assertThatThrownBy(() -> channelService.changeChannelPolicy(10L, 30L, true, false, true))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("채널 정책을 변경할 권한이 없습니다.");

        verify(channelStoragePort, never()).update(any(Channel.class));
    }

    @Test
    void 권한이_있으면_채널_정책을_변경한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelService channelService = new ChannelService(channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(30L),
                ChannelMembership.create(UserId.create(30L), ChannelRole.OWNER, createdAt)
        );
        Channel channel = Channel.create(
                10L,
                "channel-10",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        channelService.changeChannelPolicy(10L, 30L, false, true, false);

        // then
        ArgumentCaptor<Channel> updatedCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelStoragePort).update(updatedCaptor.capture());
        Channel updatedChannel = updatedCaptor.getValue();

        ChannelPolicy policy = updatedChannel.getChannelPolicy();
        assertAll(
                () -> assertThat(policy.canEditOwnMessage()).isFalse(),
                () -> assertThat(policy.canDeleteOwnMessage()).isTrue(),
                () -> assertThat(policy.isPublic()).isFalse()
        );
    }

    @Test
    void 권한이_없으면_채널_이름을_변경할_수_없다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelService channelService = new ChannelService(channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(40L),
                ChannelMembership.create(UserId.create(40L), ChannelRole.MEMBER, createdAt)
        );
        Channel channel = Channel.create(
                11L,
                "channel-11",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when & then
        assertThatThrownBy(() -> channelService.changeChannelName(11L, 40L, "new-name"))
                .isInstanceOf(ChannelOperationForbiddenException.class)
                .hasMessage("채널 이름을 변경할 권한이 없습니다.");

        verify(channelStoragePort, never()).update(any(Channel.class));
    }

    @Test
    void 권한이_있으면_채널_이름을_변경한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ChannelService channelService = new ChannelService(channelStoragePort);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(40L),
                ChannelMembership.create(UserId.create(40L), ChannelRole.OWNER, createdAt)
        );
        Channel channel = Channel.create(
                11L,
                "channel-11",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        // when
        channelService.changeChannelName(11L, 40L, "new-name");

        // then
        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(channelStoragePort).update(captor.capture());
        Channel updatedChannel = captor.getValue();

        assertThat(updatedChannel.getName()).isEqualTo("new-name");
    }
}
