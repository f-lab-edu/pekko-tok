package com.tok.pekko.domain.channel.model;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChannelDomainEventTest {

    @Test
    void 채널_도메인_이벤트를_통해_채널_이름을_변경한다() {
        // given
        Channel channel = mock(Channel.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelDomainEvent event = new ChannelDomainEvent.ChannelNameEdited(1L, 1L, "new-name", occurredAt);

        // when
        event.apply(channel);

        // then
        verify(channel, times(1)).applyChannelNameEdited("new-name");
        verifyNoMoreInteractions(channel);
    }

    @Test
    void 채널_도메인_이벤트를_통해_채널_정책을_변경한다() {
        // given
        Channel channel = mock(Channel.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelDomainEvent event = new ChannelDomainEvent.ChannelPolicyChanged(1L, 1L, false, false, false, occurredAt);

        // when
        event.apply(channel);

        // then
        verify(channel, times(1)).applyChannelPolicyChanged(false, false, false);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void 채널_도메인_이벤트를_통해_멤버가_추가된다() {
        // given
        Channel channel = mock(Channel.class);
        LocalDateTime joinedAt = LocalDateTime.now();
        ChannelDomainEvent event = new ChannelDomainEvent.UserJoined(
                1L,
                2L,
                ChannelRole.MEMBER.name(),
                List.of(),
                joinedAt
        );

        // when
        event.apply(channel);

        // then
        verify(channel, times(1)).applyUserJoined(1L, 2L, ChannelRole.MEMBER.name(), List.of(), joinedAt);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void 채널_도메인_이벤트를_통해_초대된_멤버가_추가된다() {
        // given
        Channel channel = mock(Channel.class);
        LocalDateTime invitedAt = LocalDateTime.now();
        ChannelDomainEvent event = new ChannelDomainEvent.UserInvited(
                1L,
                2L,
                ChannelRole.MEMBER.name(),
                List.of(),
                invitedAt
        );

        // when
        event.apply(channel);

        // then
        verify(channel, times(1)).applyUserInvited(1L, 2L, ChannelRole.MEMBER.name(), List.of(), invitedAt);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void 채널_도메인_이벤트를_통해_멤버가_나간다() {
        // given
        Channel channel = mock(Channel.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelDomainEvent event = new ChannelDomainEvent.MemberLeft(1L, 2L, occurredAt);

        // when
        event.apply(channel);

        // then
        verify(channel, times(1)).applyMemberLeft(2L);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void 채널_도메인_이벤트를_통해_멤버가_강퇴된다() {
        // given
        Channel channel = mock(Channel.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelDomainEvent event = new ChannelDomainEvent.MemberKicked(1L, 2L, occurredAt);

        // when
        event.apply(channel);

        // then
        verify(channel, times(1)).applyMemberKicked(2L);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void 채널_도메인_이벤트를_통해_매니저로_승격된다() {
        // given
        Channel channel = mock(Channel.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelDomainEvent event = new ChannelDomainEvent.PromotedToManager(
                2L,
                List.of(ChannelPermissionType.MESSAGE_EDIT.name()),
                occurredAt
        );

        // when
        event.apply(channel);

        // then
        verify(channel, times(1)).applyPromotedToManager(2L, List.of(ChannelPermissionType.MESSAGE_EDIT.name()));
        verifyNoMoreInteractions(channel);
    }

    @Test
    void 채널_도메인_이벤트를_통해_멤버로_강등된다() {
        // given
        Channel channel = mock(Channel.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelDomainEvent event = new ChannelDomainEvent.DemotedToMember(1L, 2L, occurredAt);

        // when
        event.apply(channel);

        // then
        verify(channel, times(1)).applyDemotedToMember(2L);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void 채널_도메인_이벤트를_통해_권한이_추가된다() {
        // given
        Channel channel = mock(Channel.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelDomainEvent event = new ChannelDomainEvent.PermissionAdded(
                1L,
                2L,
                ChannelPermissionType.MEMBER_KICK.name(),
                occurredAt
        );

        // when
        event.apply(channel);

        // then
        verify(channel, times(1)).applyPermissionAdded(2L, ChannelPermissionType.MEMBER_KICK.name());
        verifyNoMoreInteractions(channel);
    }

    @Test
    void 채널_도메인_이벤트를_통해_권한이_삭제된다() {
        // given
        Channel channel = mock(Channel.class);
        LocalDateTime occurredAt = LocalDateTime.now();
        ChannelDomainEvent event = new ChannelDomainEvent.PermissionRemoved(
                1L,
                2L,
                ChannelPermissionType.MEMBER_KICK.name(),
                occurredAt
        );

        // when
        event.apply(channel);

        // then
        verify(channel, times(1)).applyPermissionRemoved(2L, ChannelPermissionType.MEMBER_KICK.name());
        verifyNoMoreInteractions(channel);
    }
}
