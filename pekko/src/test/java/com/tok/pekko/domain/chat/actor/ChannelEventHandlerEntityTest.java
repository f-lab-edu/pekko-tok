package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.ChannelRole;
import com.tok.pekko.domain.channel.model.vo.ChannelId;
import com.tok.pekko.domain.channel.model.vo.ChannelManagePermissions;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChannelEntity.DomainEventProcessed;
import com.tok.pekko.domain.chat.actor.ChannelEntity.SyncStoredMembership;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleChannelNameEdited;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleChannelPolicyChanged;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleDemotedToMember;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleMemberKicked;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleMemberLeft;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleMessageDeleted;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleMessageEdited;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandlePermissionAdded;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandlePermissionRemoved;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandlePromotedToManager;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleUserInvited;
import com.tok.pekko.domain.chat.actor.ChannelEventHandlerEntity.HandleUserJoined;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.ChannelNameEdited;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.ChannelPolicyChanged;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.DemotedToMember;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.MemberKicked;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.MemberLeft;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.MessageDeleted;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.MessageEdited;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.PermissionAdded;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.PermissionRemoved;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.PromotedToManager;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.UserInvited;
import com.tok.pekko.domain.chat.actor.ChatDomainEvent.UserJoined;
import com.tok.pekko.domain.chat.port.in.ChannelProtocol.ChannelEntityCommand;
import com.tok.pekko.domain.chat.port.out.ChannelActorStoragePort;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.ChannelEventHandlerCommand;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.EventFailed;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.EventSucceeded;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.NotifyStoredMembership;
import com.tok.pekko.domain.chat.port.out.ChannelEventHandlerProtocol.Shutdown;
import com.tok.pekko.domain.chat.port.out.ChannelMembershipActorStoragePort;
import com.tok.pekko.domain.chat.port.out.MessageStoragePort;
import com.tok.pekko.domain.user.model.vo.UserId;
import java.time.Duration;
import java.time.LocalDateTime;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"NonAsciiCharacters", "unchecked"})
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ChannelEventHandlerEntityTest {

    private static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void tearDown() {
        testKit.shutdownTestKit();
    }

    @Test
    void HandleChannelPolicyChanged_메시지를_받으면_ChannelActorStoragePort에_채널_업데이트를_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelActorStoragePort).update(any(Channel.class), any(Long.class), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChannelPolicy newPolicy = ChannelPolicy.defaultPolicy().updatePublic(false);
        Channel channel = Channel.create("test", 1L, newPolicy, LocalDateTime.now());
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelPolicyChanged event = new ChannelPolicyChanged(channelId, 1L, occurredAt, channel);

        // when
        eventHandler.tell(new HandleChannelPolicyChanged(event));

        // then
        verify(channelActorStoragePort, timeout(1000)).update(eq(channel), eq(1L), any());
    }

    @Test
    void HandleChannelNameEdited_메시지를_받으면_ChannelActorStoragePort에_채널_업데이트를_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelActorStoragePort).update(any(Channel.class), any(Long.class), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        Channel channel = Channel.create("edited-name", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelNameEdited event = new ChannelNameEdited(channelId, 1L, occurredAt, channel);

        // when
        eventHandler.tell(new HandleChannelNameEdited(event));

        // then
        verify(channelActorStoragePort, timeout(1000)).update(eq(channel), eq(1L), any());
    }

    @Test
    void HandleUserJoined_메시지를_받으면_ChannelMembershipActorStoragePort에_join을_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelMembershipActorStoragePort).join(any(), any(), any(), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChannelMembership membership = ChannelMembership.create(
                ChannelId.create(channelId),
                UserId.create(2L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );
        LocalDateTime occurredAt = LocalDateTime.now();
        UserJoined event = new UserJoined(channelId, 1L, occurredAt, membership);

        // when
        eventHandler.tell(new HandleUserJoined(event));

        // then
        verify(channelMembershipActorStoragePort, timeout(1000)).join(
                eq(1L),
                eq(membership.getChannelId()),
                eq(membership),
                any()
        );
    }

    @Test
    void HandleMemberLeft_메시지를_받으면_ChannelMembershipActorStoragePort에_leave를_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelMembershipActorStoragePort).leave(any(), any(), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChannelMembership membership = ChannelMembership.create(
                ChannelId.create(channelId),
                UserId.create(2L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );
        LocalDateTime occurredAt = LocalDateTime.now();
        MemberLeft event = new MemberLeft(channelId, 1L, occurredAt, membership);

        // when
        eventHandler.tell(new HandleMemberLeft(event));

        // then
        verify(channelMembershipActorStoragePort, timeout(1000)).leave(eq(1L), eq(membership), any());
    }

    @Test
    void HandleUserInvited_메시지를_받으면_ChannelMembershipActorStoragePort에_inviteUser를_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelMembershipActorStoragePort).inviteUser(any(), any(), any(), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChannelMembership membership = ChannelMembership.create(
                ChannelId.create(channelId),
                UserId.create(3L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );
        LocalDateTime occurredAt = LocalDateTime.now();
        UserInvited event = new UserInvited(channelId, 1L, occurredAt, membership);

        // when
        eventHandler.tell(new HandleUserInvited(event));

        // then
        verify(channelMembershipActorStoragePort, timeout(1000)).inviteUser(
                eq(1L),
                eq(membership.getChannelId()),
                eq(membership),
                any()
        );
    }

    @Test
    void HandlePromotedToManager_메시지를_받으면_ChannelMembershipActorStoragePort에_promoteToManager를_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelMembershipActorStoragePort).promoteToManager(any(), any(), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChannelMembership membership = ChannelMembership.create(
                1L,
                channelId,
                2L,
                ChannelRole.MANAGER,
                ChannelManagePermissions.ofManager(),
                LocalDateTime.now()
        );
        LocalDateTime occurredAt = LocalDateTime.now();
        PromotedToManager event = new PromotedToManager(channelId, 1L, occurredAt, membership);

        // when
        eventHandler.tell(new HandlePromotedToManager(event));

        // then
        verify(channelMembershipActorStoragePort, timeout(1000)).promoteToManager(eq(1L), eq(membership), any());
    }

    @Test
    void HandleDemotedToMember_메시지를_받으면_ChannelMembershipActorStoragePort에_demoteToMember를_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelMembershipActorStoragePort).demoteToMember(any(), any(), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChannelMembership membership = ChannelMembership.create(
                ChannelId.create(channelId),
                UserId.create(2L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );
        LocalDateTime occurredAt = LocalDateTime.now();
        DemotedToMember event = new DemotedToMember(channelId, 1L, occurredAt, membership);

        // when
        eventHandler.tell(new HandleDemotedToMember(event));

        // then
        verify(channelMembershipActorStoragePort, timeout(1000)).demoteToMember(eq(1L), eq(membership), any());
    }

    @Test
    void HandlePermissionAdded_메시지를_받으면_ChannelMembershipActorStoragePort에_addPermission을_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelMembershipActorStoragePort).addPermission(any(), any(), any(), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChannelMembership membership = ChannelMembership.create(
                ChannelId.create(channelId),
                UserId.create(2L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );
        ChannelPermissionType permission = ChannelPermissionType.MESSAGE_EDIT;
        LocalDateTime occurredAt = LocalDateTime.now();
        PermissionAdded event = new PermissionAdded(channelId, 1L, occurredAt, membership, permission);

        // when
        eventHandler.tell(new HandlePermissionAdded(event));

        // then
        verify(channelMembershipActorStoragePort, timeout(1000)).addPermission(
                eq(1L),
                eq(membership),
                eq(permission),
                any()
        );
    }

    @Test
    void HandlePermissionRemoved_메시지를_받으면_ChannelMembershipActorStoragePort에_removePermission을_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelMembershipActorStoragePort).removePermission(any(), any(), any(), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChannelMembership membership = ChannelMembership.create(
                ChannelId.create(channelId),
                UserId.create(2L),
                ChannelRole.MANAGER,
                LocalDateTime.now()
        );
        ChannelPermissionType permission = ChannelPermissionType.MEMBER_KICK;
        LocalDateTime occurredAt = LocalDateTime.now();
        PermissionRemoved event = new PermissionRemoved(channelId, 1L, occurredAt, membership, permission);

        // when
        eventHandler.tell(new HandlePermissionRemoved(event));

        // then
        verify(channelMembershipActorStoragePort, timeout(1000)).removePermission(
                eq(1L),
                eq(membership),
                eq(permission),
                any()
        );
    }

    @Test
    void HandleMemberKicked_메시지를_받으면_ChannelMembershipActorStoragePort에_kickMember를_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelMembershipActorStoragePort).kickMember(any(), any(), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChannelMembership membership = ChannelMembership.create(
                ChannelId.create(channelId),
                UserId.create(2L),
                ChannelRole.MEMBER,
                LocalDateTime.now()
        );
        LocalDateTime occurredAt = LocalDateTime.now();
        MemberKicked event = new MemberKicked(channelId, 1L, occurredAt, membership);

        // when
        eventHandler.tell(new HandleMemberKicked(event));

        // then
        verify(channelMembershipActorStoragePort, timeout(1000)).kickMember(eq(1L), eq(membership), any());
    }

    @Test
    void HandleMessageEdited_메시지를_받으면_MessageStoragePort에_update를_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(messageStoragePort).update(anyLong(), any(ChatMessage.class), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChatMessage updatedMessage = new ChatMessage(
                1L,
                channelId,
                100L,
                1L,
                "Updated message",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        LocalDateTime occurredAt = LocalDateTime.now();
        MessageEdited event = new MessageEdited(channelId, 1L, occurredAt, updatedMessage);

        // when
        eventHandler.tell(new HandleMessageEdited(event));

        // then
        verify(messageStoragePort, timeout(1000)).update(eq(1L), eq(updatedMessage), any());
    }

    @Test
    void HandleMessageDeleted_메시지를_받으면_MessageStoragePort에_delete를_요청한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(messageStoragePort).delete(any(), any(), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        Long deletedMessageId = 10L;
        LocalDateTime occurredAt = LocalDateTime.now();
        MessageDeleted event = new MessageDeleted(channelId, 1L, occurredAt, deletedMessageId);

        // when
        eventHandler.tell(new HandleMessageDeleted(event));

        // then
        verify(messageStoragePort, timeout(1000)).delete(eq(1L), eq(deletedMessageId), any());
    }

    @Test
    void EventSucceeded_메시지를_받으면_ChannelEntity에_DomainEventProcessed를_전송한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelActorStoragePort).update(any(Channel.class), any(Long.class), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        Channel channel = Channel.create("test", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelNameEdited domainEvent = new ChannelNameEdited(channelId, 1L, occurredAt, channel);
        eventHandler.tell(new HandleChannelNameEdited(domainEvent));

        // when
        eventHandler.tell(new EventSucceeded(1L));

        // then
        verify(channelEntity, timeout(1000)).tell(any(DomainEventProcessed.class));
    }

    @Test
    void EventFailed_메시지를_받으면_ChannelEntity에_DomainEventProcessed를_전송한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelActorStoragePort).update(any(Channel.class), any(Long.class), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        Channel channel = Channel.create("test", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelNameEdited domainEvent = new ChannelNameEdited(channelId, 1L, occurredAt, channel);
        eventHandler.tell(new HandleChannelNameEdited(domainEvent));

        Throwable error = new RuntimeException("Storage failed");

        // when
        eventHandler.tell(new EventFailed(1L, error));

        // then
        verify(channelEntity, timeout(1000)).tell(any(DomainEventProcessed.class));
    }

    @Test
    void NotifyStoredMembership_메시지를_받으면_ChannelEntity에_SyncStoredMembership을_전송한다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        ChannelMembership membership = ChannelMembership.create(
                1L,
                channelId,
                2L,
                ChannelRole.MEMBER,
                ChannelManagePermissions.ofMember(),
                LocalDateTime.now()
        );

        // when
        eventHandler.tell(new NotifyStoredMembership(membership));

        // then
        verify(channelEntity, timeout(1000)).tell(any(SyncStoredMembership.class));
    }

    @Test
    void Shutdown_메시지를_받으면_ChannelEventHandlerEntity가_종료된다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        TestProbe<ChannelEventHandlerCommand> probe = testKit.createTestProbe();
        probe.expectNoMessage(Duration.ofMillis(100));

        // when
        eventHandler.tell(new Shutdown());

        // then
        probe.expectTerminated(eventHandler, Duration.ofSeconds(3));
    }

    @Test
    void 동일한_eventId로_여러_번_메시지를_받으면_중복_처리하지_않는다() {
        // given
        Long channelId = 1L;
        ClusterSharding clusterSharding = mock(ClusterSharding.class);
        EntityRef<ChannelEntityCommand> channelEntity = mock(EntityRef.class);

        when(clusterSharding.entityRefFor(any(EntityTypeKey.class), anyString())).thenReturn(channelEntity);

        MessageStoragePort messageStoragePort = mock(MessageStoragePort.class);
        ChannelActorStoragePort channelActorStoragePort = mock(ChannelActorStoragePort.class);
        ChannelMembershipActorStoragePort channelMembershipActorStoragePort = mock(ChannelMembershipActorStoragePort.class);

        doNothing().when(channelActorStoragePort).update(any(Channel.class), any(Long.class), any());

        ActorRef<ChannelEventHandlerCommand> eventHandler = testKit.spawn(
                ChannelEventHandlerEntity.create(
                        clusterSharding,
                        channelId,
                        messageStoragePort,
                        channelActorStoragePort,
                        channelMembershipActorStoragePort
                )
        );

        Channel channel = Channel.create("test", 1L, ChannelPolicy.defaultPolicy(), LocalDateTime.now());
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelNameEdited event = new ChannelNameEdited(channelId, 1L, occurredAt, channel);

        // when
        eventHandler.tell(new HandleChannelNameEdited(event));
        eventHandler.tell(new HandleChannelNameEdited(event));
        eventHandler.tell(new HandleChannelNameEdited(event));

        // then
        verify(channelActorStoragePort, timeout(1000)).update(eq(channel), eq(1L), any());
    }
}
