package com.tok.pekko.application.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tok.pekko.application.actor.ClientSessionActorManagementService;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.chat.actor.ChannelEntity;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyDemotedToMember;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyMemberKicked;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyMemberLeft;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyPermissionAdded;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyPermissionRemoved;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyPromotedToManager;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyUserInvited;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ApplyUserJoined;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"NonAsciiCharacters", "unchecked"})
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelMembershipServiceTest {

    @Test
    void 채널_참여를_ChannelEntity에_전달한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2024-03-01T12:30:00Z"), ZoneOffset.UTC);
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("1")))
                .thenReturn(entityRef);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                clusterSharding,
                clientSessionActorManagementService
        );

        // when
        service.joinChannel(1L, 10L);

        // then
        ArgumentCaptor<ApplyUserJoined> captor = ArgumentCaptor.forClass(ApplyUserJoined.class);

        verify(entityRef).tell(captor.capture());
        ApplyUserJoined command = captor.getValue();

        assertAll(
                () -> assertThat(command.channelId()).isEqualTo(1L),
                () -> assertThat(command.userId()).isEqualTo(UserId.create(10L)),
                () -> assertThat(command.role()).isEqualTo("MEMBER"),
                () -> assertThat(command.managerPermissions()).isEmpty(),
                () -> verify(clientSessionActorManagementService).syncJoinChannel(1L, 10L)
        );
    }

    @Test
    void 채널_초대를_ChannelEntity에_전달한다() {
        // given
        Clock clock = Clock.systemUTC();
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("2")))
                .thenReturn(entityRef);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                clusterSharding,
                clientSessionActorManagementService
        );

        // when
        service.inviteMember(2L, 30L, 40L);

        // then
        ArgumentCaptor<ApplyUserInvited> captor = ArgumentCaptor.forClass(ApplyUserInvited.class);

        verify(entityRef).tell(captor.capture());
        ApplyUserInvited command = captor.getValue();

        assertAll(
                () -> assertThat(command.channelId()).isEqualTo(2L),
                () -> assertThat(command.inviterId()).isEqualTo(UserId.create(30L)),
                () -> assertThat(command.inviteeId()).isEqualTo(UserId.create(40L)),
                () -> assertThat(command.role()).isEqualTo("MEMBER"),
                () -> verify(clientSessionActorManagementService).syncJoinChannel(2L, 40L)
        );
    }

    @Test
    void 채널_탈퇴를_ChannelEntity에_전달한다() {
        // given
        Clock clock = Clock.systemUTC();
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("3")))
                .thenReturn(entityRef);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                clusterSharding,
                clientSessionActorManagementService
        );

        // when
        service.leaveChannel(3L, 50L);

        // then
        ArgumentCaptor<ApplyMemberLeft> captor = ArgumentCaptor.forClass(ApplyMemberLeft.class);

        verify(entityRef).tell(captor.capture());
        ApplyMemberLeft command = captor.getValue();

        assertAll(
                () -> assertThat(command.channelId()).isEqualTo(3L),
                () -> assertThat(command.userId()).isEqualTo(UserId.create(50L)),
                () -> verify(clientSessionActorManagementService).syncLeaveChannel(3L, 50L)
        );
    }

    @Test
    void 매니저_승격을_ChannelEntity에_전달한다() {
        // given
        Clock clock = Clock.systemUTC();
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("4")))
                .thenReturn(entityRef);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                clusterSharding,
                clientSessionActorManagementService
        );

        // when
        service.promoteToManager(4L, 60L, 70L);

        // then
        ArgumentCaptor<ApplyPromotedToManager> captor = ArgumentCaptor.forClass(ApplyPromotedToManager.class);

        verify(entityRef).tell(captor.capture());
        ApplyPromotedToManager command = captor.getValue();

        assertAll(
                () -> assertThat(command.targetUserId()).isEqualTo(UserId.create(70L)),
                () -> assertThat(command.executorId()).isEqualTo(UserId.create(60L)),
                () -> assertThat(command.managerPermissions())
                        .containsExactly(ChannelPermissionType.MESSAGE_EDIT.name())
        );
    }

    @Test
    void 매니저_강등을_ChannelEntity에_전달한다() {
        // given
        Clock clock = Clock.systemUTC();
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("5")))
                .thenReturn(entityRef);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                clusterSharding,
                clientSessionActorManagementService
        );

        // when
        service.demoteToMember(5L, 80L, 90L);

        // then
        ArgumentCaptor<ApplyDemotedToMember> captor = ArgumentCaptor.forClass(ApplyDemotedToMember.class);

        verify(entityRef).tell(captor.capture());
        ApplyDemotedToMember command = captor.getValue();

        assertAll(
                () -> assertThat(command.channelId()).isEqualTo(5L),
                () -> assertThat(command.targetUserId()).isEqualTo(UserId.create(90L))
        );
    }

    @Test
    void 권한_추가를_ChannelEntity에_전달한다() {
        // given
        Clock clock = Clock.systemUTC();
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("6")))
                .thenReturn(entityRef);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                clusterSharding,
                clientSessionActorManagementService
        );

        // when
        service.addPermission(6L, 100L, 110L, ChannelPermissionType.MEMBER_KICK);

        // then
        ArgumentCaptor<ApplyPermissionAdded> captor = ArgumentCaptor.forClass(ApplyPermissionAdded.class);

        verify(entityRef).tell(captor.capture());
        ApplyPermissionAdded command = captor.getValue();

        assertAll(
                () -> assertThat(command.channelId()).isEqualTo(6L),
                () -> assertThat(command.grantorId()).isEqualTo(UserId.create(100L)),
                () -> assertThat(command.granteeId()).isEqualTo(UserId.create(110L)),
                () -> assertThat(command.permissionType()).isEqualTo(ChannelPermissionType.MEMBER_KICK.name())
        );
    }

    @Test
    void 권한_삭제를_ChannelEntity에_전달한다() {
        // given
        Clock clock = Clock.systemUTC();
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("7")))
                .thenReturn(entityRef);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                clusterSharding,
                clientSessionActorManagementService
        );

        // when
        service.removePermission(7L, 120L, 130L, ChannelPermissionType.MEMBER_KICK);

        // then
        ArgumentCaptor<ApplyPermissionRemoved> captor = ArgumentCaptor.forClass(ApplyPermissionRemoved.class);

        verify(entityRef).tell(captor.capture());
        ApplyPermissionRemoved command = captor.getValue();

        assertAll(
                () -> assertThat(command.channelId()).isEqualTo(7L),
                () -> assertThat(command.grantorId()).isEqualTo(UserId.create(120L)),
                () -> assertThat(command.granteeId()).isEqualTo(UserId.create(130L)),
                () -> assertThat(command.permissionType()).isEqualTo(ChannelPermissionType.MEMBER_KICK.name())
        );
    }

    @Test
    void 강퇴를_ChannelEntity에_전달한다() {
        // given
        Clock clock = Clock.systemUTC();
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        ClientSessionActorManagementService clientSessionActorManagementService = mock(ClientSessionActorManagementService.class);
        EntityRef<ChannelEntityCommand> entityRef = mock(EntityRef.class);
        when(clusterSharding.entityRefFor(eq(ChannelEntity.ENTITY_TYPE_KEY), eq("8")))
                .thenReturn(entityRef);
        ChannelMembershipService service = new ChannelMembershipService(
                clock,
                clusterSharding,
                clientSessionActorManagementService
        );

        // when
        service.kickMember(8L, 140L, 150L);

        // then
        ArgumentCaptor<ApplyMemberKicked> captor = ArgumentCaptor.forClass(ApplyMemberKicked.class);

        verify(entityRef).tell(captor.capture());
        ApplyMemberKicked command = captor.getValue();

        assertAll(
                () -> assertThat(command.channelId()).isEqualTo(8L),
                () -> assertThat(command.executorId()).isEqualTo(UserId.create(140L)),
                () -> assertThat(command.targetUserId()).isEqualTo(UserId.create(150L)),
                () -> verify(clientSessionActorManagementService, times(0)).syncJoinChannel(any(), any())
        );
    }
}
