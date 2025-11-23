package com.tok.pekko.domain.chat.actor;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import java.time.LocalDateTime;

public interface ChannelDomainEvent {
    
    Long channelId();

    Long eventId();
    
    LocalDateTime occurredAt();

    // 채널 메타데이터 관련 도메인 이벤트
    interface ChannelMetadataEvent extends ChannelDomainEvent { }

    // 채널 정책 변경 도메인 이벤트
    record ChannelPolicyChanged(Long channelId, Long eventId, LocalDateTime occurredAt, Channel channel) implements ChannelMetadataEvent { }

    // 채널 이름 변경 도메인 이벤트
    record ChannelNameEdited(Long channelId, Long eventId, LocalDateTime occurredAt, Channel channel) implements ChannelMetadataEvent { }

    // 채널 참여자가 변경되는 도메인 이벤트
    interface ChannelMembershipEvent extends ChannelDomainEvent { }

    // 채널 참여 도메인 이벤트
    record UserJoined(Long channelId, Long eventId, LocalDateTime occurredAt, ChannelMembership channelMembership) implements ChannelMembershipEvent { }

    // 채널 탈퇴 도메인 이벤트
    record MemberLeft(Long channelId, Long eventId, LocalDateTime occurredAt, ChannelMembership channelMembership) implements ChannelMembershipEvent { }

    // 채널 초대 도메인 이벤트
    record UserInvited(Long channelId, Long eventId, LocalDateTime occurredAt, ChannelMembership channelMembership) implements ChannelMembershipEvent { }

    // 채널 강퇴 도메인 이벤트
    record MemberKicked(Long channelId, Long eventId, LocalDateTime occurredAt, ChannelMembership channelMembership) implements ChannelMembershipEvent { }

    // 매니저 승격 도메인 이벤트
    record PromotedToManager(Long channelId, Long eventId, LocalDateTime occurredAt, ChannelMembership channelMembership) implements ChannelMembershipEvent { }

    // 멤버 강등 도메인 이벤트
    record DemotedToMember(Long channelId, Long eventId, LocalDateTime occurredAt, ChannelMembership channelMembership) implements ChannelMembershipEvent { }

    // 권한 추가 도메인 이벤트
    record PermissionAdded(Long channelId, Long eventId, LocalDateTime occurredAt, ChannelMembership channelMembership, ChannelPermissionType permissionType) implements ChannelMembershipEvent { }

    // 권한 삭제 도메인 이벤트
    record PermissionRemoved(Long channelId, Long eventId, LocalDateTime occurredAt, ChannelMembership channelMembership, ChannelPermissionType permissionType) implements ChannelMembershipEvent { }

    // 채팅 메시지 관련 도메인 이벤트
    interface MessageEvent extends ChannelDomainEvent { }

    // 메시지 수정 도메인 이벤트
    record MessageEdited(Long channelId, Long eventId, LocalDateTime occurredAt, ChatMessage updatedMessage) implements MessageEvent { }

    // 메시지 삭제 도메인 이벤트
    record MessageDeleted(Long channelId, Long eventId, LocalDateTime occurredAt, Long deletedMessageId) implements MessageEvent { }
}
