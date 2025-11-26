package com.tok.pekko.application.channel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.AddPermission;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.InviteUser;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.PromoteToManager;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.RemovePermission;
import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelMembershipServiceTest {

    @Test
    void 멤버가_채널에_참여하면_ChannelEntity에_join을_요청하고_syncJoinChannel을_호출한다() {
        // given
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        when(clusterSharding.<ChannelEntityCommand>entityRefFor(ChannelEntity.ENTITY_TYPE_KEY, "1"))
                .thenReturn(entityRef);
        when(entityRef.ask(any(), any(Duration.class))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        ChannelMembershipService service = new ChannelMembershipService(clusterSharding, clientSessionActorManagementService);

        // when
        service.joinChannel(1L, 10L);

        // then
        verify(clientSessionActorManagementService, times(1)).syncJoinChannel(1L, 10L);
        verify(entityRef, times(1)).ask(any(), any(Duration.class));
    }

    @Test
    void 멤버를_초대하면_ChannelEntity에_초대_메시지를_보낸다() {
        // given
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = Mockito.mock(EntityRef.class);
        ClusterSharding clusterSharding = Mockito.mock(ClusterSharding.class);
        when(clusterSharding.<ChannelEntityCommand>entityRefFor(ChannelEntity.ENTITY_TYPE_KEY, "2"))
                .thenReturn(entityRef);
        ChannelMembershipService service = new ChannelMembershipService(clusterSharding, clientSessionActorManagementService);

        // when
        service.inviteMember(2L, 30L, 40L);

        // then
        verify(entityRef, times(1)).tell(any(InviteUser.class));
    }

    @Test
    void 승격과_권한_부여_메시지가_ChannelEntity로_전파된다() {
        // given
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = Mockito.mock(EntityRef.class);
        ClusterSharding clusterSharding = Mockito.mock(ClusterSharding.class);
        when(clusterSharding.<ChannelEntityCommand>entityRefFor(ChannelEntity.ENTITY_TYPE_KEY, "3"))
                .thenReturn(entityRef);
        ChannelMembershipService service = new ChannelMembershipService(clusterSharding, clientSessionActorManagementService);

        // when
        service.promoteToManager(3L, 1L, 2L);
        service.addPermission(3L, 1L, 2L, ChannelPermissionType.MEMBER_INVITE);
        service.removePermission(3L, 1L, 2L, ChannelPermissionType.MEMBER_INVITE);

        // then
        verify(entityRef, times(1)).tell(any(PromoteToManager.class));
        verify(entityRef, times(1)).tell(any(AddPermission.class));
        verify(entityRef, times(1)).tell(any(RemovePermission.class));
    }
}
