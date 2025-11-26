package com.tok.pekko.application.channel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.channel.port.out.ChannelStoragePort;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChangeChannelPolicy;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.EditChannelName;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Optional;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionTemplate;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelServiceTest {

    @Test
    void 채널을_생성하면_스토리지에_저장한다() {
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            channelStoragePort.delete(ChannelId.create(10L));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        ChannelService channelService = new ChannelService(clusterSharding, channelStoragePort, transactionTemplate);

        Channel storedChannel = Channel.create(
                1L,
                "general",
                1L,
                ChannelPolicy.defaultPolicy(),
                new HashMap<>(),
                LocalDateTime.ofInstant(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
        when(channelStoragePort.store(any(Channel.class))).thenReturn(storedChannel);

        channelService.createChannel("general", 1L);

        verify(channelStoragePort, times(1)).store(any(Channel.class));
    }

    @Test
    void 채널_삭제시_스토리지_삭제와_Actor_Shutdown을_전파한다() {
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        EntityRef<ChannelEntityCommand> channelEntityRef = Mockito.mock(EntityRef.class);
        EntityRef<ChannelEventHandlerCommand> handlerRef = Mockito.mock(EntityRef.class);

        when(clusterSharding.<ChannelEntityCommand>entityRefFor(ChannelEntity.ENTITY_TYPE_KEY, "10"))
                .thenReturn(channelEntityRef);
        when(clusterSharding.<ChannelEventHandlerCommand>entityRefFor(ChannelEventHandlerEntity.ENTITY_TYPE_KEY, "10"))
                .thenReturn(handlerRef);
        ChannelService channelService = new ChannelService(clusterSharding, channelStoragePort, transactionTemplate);
        Channel channel = mock(Channel.class);
        when(channel.getChannelId()).thenReturn(ChannelId.create(10L));
        doAnswer(invocation -> null).when(channel).validateDeleteChannel(any());
        when(channelStoragePort.findChannel(anyLong(), anyLong())).thenReturn(Optional.of(channel));

        channelService.deleteChannel(10L, 20L);

        verify(transactionTemplate, times(1)).executeWithoutResult(any());
        verify(channelEntityRef, times(1)).tell(any(ChannelProtocol.Shutdown.class));
        verify(handlerRef, times(1)).tell(any(ChannelEventHandlerProtocol.Shutdown.class));
    }

    @Test
    void 정책_변경은_ChannelEntity에_ChangeChannelPolicy를_전파한다() {
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        EntityRef<ChannelEntityCommand> channelEntityRef = Mockito.mock(EntityRef.class);
        when(clusterSharding.<ChannelEntityCommand>entityRefFor(ChannelEntity.ENTITY_TYPE_KEY, "5"))
                .thenReturn(channelEntityRef);
        ChannelService channelService = new ChannelService(clusterSharding, channelStoragePort, transactionTemplate);

        channelService.changeChannelPolicy(5L, 30L, false, true, false);

        verify(channelEntityRef, times(1)).tell(any(ChangeChannelPolicy.class));
    }

    @Test
    void 이름_변경은_ChannelEntity에_EditChannelName을_전파한다() {
        ChannelStoragePort channelStoragePort = mock(ChannelStoragePort.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        EntityRef<ChannelEntityCommand> channelEntityRef = Mockito.mock(EntityRef.class);
        when(clusterSharding.<ChannelEntityCommand>entityRefFor(ChannelEntity.ENTITY_TYPE_KEY, "7"))
                .thenReturn(channelEntityRef);
        ChannelService channelService = new ChannelService(clusterSharding, channelStoragePort, transactionTemplate);

        channelService.editChannelName(7L, 50L, "new-name");

        verify(channelEntityRef, times(1)).tell(any(EditChannelName.class));
    }
}
