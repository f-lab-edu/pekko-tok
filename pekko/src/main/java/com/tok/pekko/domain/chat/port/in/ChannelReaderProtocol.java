package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import java.time.LocalDateTime;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelReaderProtocol {

    interface ChannelReaderCommand extends CborSerializable { }

    // ChannelEntity에 동기화된 채팅 히스토리를 전달받는 메시지 : ChannelEntity -> ChannelReaderActor
    record SyncNewMessage(ChatMessage message) implements ChannelReaderCommand { }

    // ChannelEntity에 동기화된 수정된 채팅 메시지를 ChannelReaderActor로 전달하는 메시지 : ChannelEntity -> ChannelReaderActor
    record SyncUpdate(Long messageId, String updatedMessage, LocalDateTime updatedAt) implements ChannelReaderCommand { }

    // ChannelEntity에 동기화된 삭제된 채팅 메시지를 ChannelReaderActor로 전달하는 메시지 : ChannelEntity -> ChannelReaderActor
    record SyncDeletion(Long messageId) implements ChannelReaderCommand { }

    // 채팅 히스토리에 대한 범위 요청을 받는 메시지 : ClientSessionActor -> ChannelReaderActor
    record GetHistory(long messageSequence, int size, ActorRef<ClientSessionCommand> replyTo) implements ChannelReaderCommand { }

    // ChannelReaderActor가 ClientSessionActor를 구독자로 등록해 채팅 이벤트를 fan-out하고 종료 신호를 감시하기 위한 메시지 : ClientSessionActor -> ChannelReaderActor
    record RegisterClientSession(Long userId, ActorRef<ClientSessionCommand> clientSession) implements ChannelReaderCommand { }

    // ClientSessionActor가 채널을 떠나거나 세션이 종료될 때 ChannelReaderActor가 해당 구독자를 해제하도록 요청하는 메시지 : ClientSessionActor -> ChannelReaderActor
    record UnregisterClientSession(Long userId) implements ChannelReaderCommand { }

    // ChannelEntity-ChannelReaderActor까지 동기화된 채팅 히스토리를 요청받는 메시지 : ClientSessionActor -> ChannelReaderActor
    record RequestInitialHistory(ActorRef<ClientSessionCommand> replyTo) implements ChannelReaderCommand { }
}
