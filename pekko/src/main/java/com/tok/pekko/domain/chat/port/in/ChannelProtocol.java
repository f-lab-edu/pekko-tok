package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.domain.channel.model.Channel;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
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
    record UpdateMessage(Long messageId, String updatedMessage) implements ChannelEntityCommand { }

    // 기존 채팅 메시지 삭제를 요청받는 메시지 : 외부 -> ChannelEntity
    record DeleteMessage(Long messageId) implements ChannelEntityCommand { }

    // 영속화된 채팅 메시지를 ChannelEntity에게 전달하는 메시지 : MessageStorageAdapter -> ChannelEntity
    record SyncPersistedMessage(ChatMessage message) implements ChannelEntityCommand { }

    // 변경 사항이 영속화된 채팅 메시지를 ChannelEntity에게 전달하는 메시지 : MessageStorageAdapter -> ChannelEntity
    record SyncUpdatedMessage(Long messageId, String updatedMessage) implements ChannelEntityCommand { }

    // 삭제된 채팅 메시지를 ChannelEntity에게 전달하는 메시지 : MessageStorageAdapter -> ChannelEntity
    record SyncDeletedMessage(Long messageId) implements ChannelEntityCommand { }

    // ChannelReaderActor가 아직 동기화받지 못한 채팅 히스토리를 ChannelEntity가 요청받는 메시지 : ChannelReaderActor -> ChannelEntity
    record ResolveHistory(long messageSequence, int size, ActorRef<ClientSessionCommand> replyTo) implements ChannelEntityCommand { }

    // ChannelEntity가 관리하고 있는 ChannelReaderActor 중 유효하지 않은 ChannelReaderActor의 제거 요청을 받는 메시지 : ChannelReaderRegistryActor -> ChannelEntity
    record RemoveShutdownReader(String readerName) implements ChannelEntityCommand { }

    // 영속화된 채널과 모든 채널 참여자를 동기화하기 위한 메시지 : 외부 -> ChannelEntity
    record SyncChannel(Channel channel) implements ChannelEntityCommand { }

    // ChannelEntity를 외부에서 종료시키기 위한 메시지 : 외부 -> ChannelEntity
    record Shutdown() implements ChannelEntityCommand { }
}
