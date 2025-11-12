package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import java.util.List;

public interface ClientSessionProtocol {

    interface ClientSessionCommand extends CborSerializable { }

    // Primary-Secondary 구조에서 완전히 동기화된 새로운 채팅 메시지를 동기화받는 메시지
    record DeliverNewMessage(ChatMessage message) implements ClientSessionCommand { }

    // Primary-Secondary까지 완전히 동기화된 수정된 채팅 메시지를 전달받는 메시지
    record DeliverUpdatedMessage(ChatMessage updatedMessage) implements ClientSessionCommand { }

    // Primary-Secondary까지 완전히 동기화된 삭제된 채팅 메시지를 전달받는 메시지
    record DeliverDeletedMessage(ChatMessage deletedMessage) implements ClientSessionCommand { }

    // 채팅 히스토리를 요청하는 메시지
    record RequestHistory(Long channelId, long messageSequence, int size) implements ClientSessionCommand { }

    // 사용자가 채널에 최초 입장했을 때 동기화된 채팅 히스토리를 전달받는 메시지
    record FoundHistory(List<ChatMessage> history) implements ClientSessionCommand { }

    // 요청한 채팅 히스토리를 전달받는 메시지
    record DeliverHistory(Long channelId, long messageSequence, int size, List<ChatMessage> history) implements ClientSessionCommand { }

    // 사용자가 참여하고 있는 모든 채널의 ID 목록을 전달받기 위한 메시지
    record FoundRegisteredChannelIds(List<Long> channelIds) implements ClientSessionCommand { }

    // 외부에서 클라이언트가 새로운 채널에 참여했음을 전파하는 메시지
    record SyncJoinChannel(Long channelId) implements ClientSessionCommand { }

    // 외부에서 클라이언트가 기존 채널에서 탈퇴했음을 전파하는 메시지
    record SyncLeaveChannel(Long channelId) implements ClientSessionCommand { }

    // ClientSessionActor 종료를 위한 메시지
    record Shutdown() implements ClientSessionCommand { }

    // Client Session인 WebSocketSession으로부터 Pong을 전달하기 위한 메시지
    record SessionPongReceived() implements ClientSessionCommand { }
}
