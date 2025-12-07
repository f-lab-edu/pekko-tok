package com.tok.pekko.application.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyChannelDeleted;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyChannelNameEdited;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyChannelPolicyChanged;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"NonAsciiCharacters", "unchecked"})
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelServiceTest {

    @Test
    void 채널을_생성하면_식별자를_반환한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        ChannelService channelService = new ChannelService(channelStoragePort, clusterSharding, clock);
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
    void 채널_삭제는_영속화_포트에_위임한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("10")))
                .thenReturn(entityRef);
        ChannelService channelService = new ChannelService(channelStoragePort, clusterSharding, clock);
        LocalDateTime createdAt = LocalDateTime.now(clock);
        Map<UserId, ChannelMembership> memberships = new HashMap<>();
        memberships.put(
                UserId.create(20L),
                ChannelMembership.create(ChannelId.create(10L), UserId.create(20L), ChannelRole.OWNER, createdAt)
        );
        Channel channel = Channel.create(
                10L,
                "channel-10",
                1L,
                ChannelPolicy.defaultPolicy(),
                memberships,
                createdAt
        );
        when(channelStoragePort.findChannel(any(Long.class), any(Long.class))).thenReturn(Optional.of(channel));

        // when
        channelService.deleteChannel(10L, 20L);

        // then
        ArgumentCaptor<ApplyChannelDeleted> captor = ArgumentCaptor.forClass(ApplyChannelDeleted.class);

        assertAll(
                () -> verify(entityRef).tell(captor.capture()),
                () -> verify(channelStoragePort).delete(channel.getChannelId()),
                () -> assertThat(captor.getValue().channelId()).isEqualTo(10L),
                () -> assertThat(captor.getValue().deleterId()).isEqualTo(UserId.create(20L))
        );
    }

    @Test
    void 채널_정책_변경은_ChannelEntity로_전달한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("10")))
                .thenReturn(entityRef);
        ChannelService channelService = new ChannelService(channelStoragePort, clusterSharding, clock);

        // when
        channelService.changeChannelPolicy(10L, 30L, true, false, true);

        // then
        ArgumentCaptor<ApplyChannelPolicyChanged> captor = ArgumentCaptor.forClass(ApplyChannelPolicyChanged.class);

        verify(entityRef).tell(captor.capture());
        ApplyChannelPolicyChanged sent = captor.getValue();

        assertAll(
                () -> assertThat(sent.channelId()).isEqualTo(10L),
                () -> assertThat(sent.changerId()).isEqualTo(UserId.create(30L)),
                () -> assertThat(sent.canEditOwnMessage()).isTrue(),
                () -> assertThat(sent.canDeleteOwnMessage()).isFalse(),
                () -> assertThat(sent.isPublic()).isTrue(),
                () -> verify(channelStoragePort, never()).update(any(Channel.class))
        );
    }

    @Test
    void 채널_이름_변경은_ChannelEntity로_전달한다() {
        // given
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        Clock clock = Clock.systemUTC();
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("11")))
                .thenReturn(entityRef);
        ChannelService channelService = new ChannelService(channelStoragePort, clusterSharding, clock);

        // when
        channelService.changeChannelName(11L, 40L, "new-name");

        // then
        ArgumentCaptor<ApplyChannelNameEdited> captor = ArgumentCaptor.forClass(ApplyChannelNameEdited.class);

        verify(entityRef).tell(captor.capture());
        ApplyChannelNameEdited sent = captor.getValue();

        assertAll(
                () -> assertThat(sent.channelId()).isEqualTo(11L),
                () -> assertThat(sent.changerId()).isEqualTo(UserId.create(40L)),
                () -> assertThat(sent.newName()).isEqualTo("new-name"),
                () -> verify(channelStoragePort, never()).update(any(Channel.class))
        );
    }
}
