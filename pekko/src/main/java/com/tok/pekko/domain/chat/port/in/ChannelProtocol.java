package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.domain.user.model.vo.UserId;
import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelProtocol {

    interface ChannelEntityCommand extends CborSerializable { }

    // 영속화된 채팅 메시지 중 최신 채팅 메시지 일부를 전달받는 메시지 : MessageStorageAdapter -> ChannelEntity
    record SyncRecentMessages(List<ChatMessage> messages) implements ChannelEntityCommand { }

    // ChannelEntity로부터 메시지를 동기화받는 ChannelReaderActor의 ActorRef를 전달하기 위한 메시지 : ChannelReaderRegistryActor -> ChannelEntity
    record RegisterReader(String readerName, ActorRef<ChannelReaderCommand> reader) implements ChannelEntityCommand { }

    // 새로운 채팅 메시지를 전달받는 메시지 : WebSocketHandler -> ChannelEntity
    record SendMessage(Long userId, String message) implements ChannelEntityCommand { }

    // 기존 채팅 메시지 수정을 요청받는 메시지 : 외부 -> ChannelEntity
    record UpdateMessage(Long executorId, Long messageId, String updatedMessage) implements ChannelEntityCommand { }

    // 기존 채팅 메시지 삭제를 요청받는 메시지 : 외부 -> ChannelEntity
    record DeleteMessage(UserId executorId, Long messageId) implements ChannelEntityCommand { }

    // 영속화된 채팅 메시지를 ChannelEntity에게 전달하는 메시지 : MessageStorageAdapter -> ChannelEntity
    record SyncPersistedMessage(ChatMessage message) implements ChannelEntityCommand { }

    // ChannelReaderActor가 아직 동기화받지 못한 채팅 히스토리를 ChannelEntity가 요청받는 메시지 : ChannelReaderActor -> ChannelEntity
    record ResolveHistory(long messageSequence, int size, ActorRef<ClientSessionCommand> replyTo) implements ChannelEntityCommand { }

    // ChannelEntity가 관리하고 있는 ChannelReaderActor 중 유효하지 않은 ChannelReaderActor의 제거 요청을 받는 메시지 : ChannelReaderRegistryActor -> ChannelEntity
    record RemoveShutdownReader(String readerName) implements ChannelEntityCommand { }

    // 채널 정책을 변경하기 위한 메시지 : 외부 -> ChannelEntity
    record ChangeChannelPolicy(UserId changerId, boolean canEditOwnMessage, boolean canDeleteOwnMessage, boolean isPublic) implements ChannelEntityCommand { }

    // 채널 이름을 변경하기 위한 메시지 : 외부 -> ChannelEntity
    record EditChannelName(UserId changerId, String changedName) implements ChannelEntityCommand { }

    // 채널에 참여한 사용자에 대한 정보를 동기화하기 위한 메시지 : 외부 -> ChannelEntity
    record JoinUser(UserId joinerId, ActorRef<ChannelEntityCommand> replyTo) implements ChannelEntityCommand { }

    // 채널에 참여했음을 전파하기 위한 메시지 : ChannelEntity -> ChannelEntity
    record JoinedUser(Long channelId, Long userId) implements ChannelEntityCommand { }

    // 채널에서 탈퇴하기 위한 메시지 : 외부 -> ChannelEntity
    record LeaveMember(UserId memberId) implements ChannelEntityCommand { }

    // 채널에 사용자를 초대하기 위한 메시지 : 외부 -> ChannelEntity
    record InviteUser(UserId inviterId, UserId inviteeId) implements ChannelEntityCommand { }

    // 역할이 MEMBER인 채널 사용자를 MANAGER로 승격시키기 위한 메시지 : 외부 -> ChannelEntity
    record PromoteToManager(UserId executorId, UserId targetUserId) implements ChannelEntityCommand { }

    // 역할이 MANAGER인 채널 사용자를 MEMBER로 강등시키기 위한 메시지 : 외부 -> ChannelEntity
    record DemoteToMember(UserId executorId, UserId targetUserId) implements ChannelEntityCommand { }

    // 역할이 MANAGER인 채널 사용자의 권한을 추가하기 위한 메시지 : 외부 -> ChannelEntity
    record AddPermission(UserId grantorId, UserId granteeId, ChannelPermissionType permission) implements ChannelEntityCommand { }

    // 역할이 MANAGER인 채널 사용자의 권한을 삭제하기 위한 메시지 : 외부 -> ChannelEntity
    record RemovePermission(UserId grantorId, UserId granteeId, ChannelPermissionType permission) implements ChannelEntityCommand { }

    // 채널 참여자를 강퇴하기 위한 메시지 : 외부 -> ChannelEntity
    record KickMember(UserId executorId, UserId targetUserId) implements ChannelEntityCommand { }

    // 영속화된 채널과 모든 채널 참여자를 동기화하기 위한 메시지 : 외부 -> ChannelEntity
    record SyncChannel(Channel channel) implements ChannelEntityCommand { }

    // ChannelEntity를 외부에서 종료시키기 위한 메시지 : 외부 -> ChannelEntity
    record Shutdown() implements ChannelEntityCommand { }

    // ChannelReaderActor가 가입한 userId에 대한 멤버십 정보를 요청하기 위한 메시지
    record ResolveMembership(UserId userId, ActorRef<ChannelReaderCommand> replyTo) implements ChannelEntityCommand { }

    // 영속화된 채널 참여자를 동기화하기 위한 메시지 : ChannelEventHandlerEntity -> ChannelEntity
    record SyncStoredMembership(ChannelMembership channelMembership) implements ChannelEntityCommand { }

    // 채널 메타데이터(이름/정책)를 조회하기 위한 메시지 : ChannelReaderActor -> ChannelEntity
    record ResolveChannelMetadata(ActorRef<ChannelReaderCommand> replyTo) implements ChannelEntityCommand { }
}
