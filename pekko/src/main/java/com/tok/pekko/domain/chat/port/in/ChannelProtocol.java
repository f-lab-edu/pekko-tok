package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelProtocol {

    interface ChannelEntityCommand extends CborSerializable { }

    // 영속화된 채팅 메시지 중 최신 채팅 메시지 일부를 전달받는 메시지
    record SyncRecentMessages(List<ChatMessage> messages) implements ChannelEntityCommand { }

    // ChannelEntity Primary에 해당하는 Secondary ChannelReaderActor의 ActorRef를 전달하기 위한 메시지
    record RegisterReader(String readerName, ActorRef<ChannelReaderCommand> reader) implements ChannelEntityCommand { }

    // 새로운 채팅 메시지를 전달받는 메시지
    record SendMessage(Long userId, String message) implements ChannelEntityCommand { }

    // 기존 채팅 메시지 수정을 요청받는 메시지
    record UpdateMessage(Long messageId, String updatedMessage) implements ChannelEntityCommand { }

    // 기존 채팅 메시지 삭제를 요청받는 메시지
    record DeleteMessage(Long messageId) implements ChannelEntityCommand { }

    // 영속화된 채팅 메시지를 ChannelEntity에게 전달하는 메시지
    record SyncPersistedMessage(ChatMessage message) implements ChannelEntityCommand { }

    // 변경 사항이 영속화된 채팅 메시지를 ChannelEntity에게 전달하는 메시지
    record SyncUpdatedMessage(Long messageId, String updatedMessage) implements ChannelEntityCommand { }

    // 삭제된 채팅 메시지를 ChannelEntity에게 전달하는 메시지
    record SyncDeletedMessage(Long messageId) implements ChannelEntityCommand { }

    // ChannelReaderActor가 아직 동기화받지 못한 채팅 히스토리를 ChannelEntity가 요청받는 메시지
    record ResolveHistory(long messageSequence, int size, ActorRef<ClientSessionCommand> replyTo) implements ChannelEntityCommand { }

    // ChannelEntity가 관리하고 있는 ChannelReaderActor 중 유효하지 않은 ChannelReaderActor의 제거 요청을 받는 메시지
    record RemoveShutdownReader(String readerName) implements ChannelEntityCommand { }
}
