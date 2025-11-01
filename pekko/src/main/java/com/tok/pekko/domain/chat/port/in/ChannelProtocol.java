package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChannelReaderProtocol.ChannelReaderCommand;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelProtocol {

    interface ChannelEntityCommand extends CborSerializable { }

    record SyncRecentMessages(List<ChatMessage> messages) implements ChannelEntityCommand { }
    record RegisterReader(String readerName, ActorRef<ChannelReaderCommand> reader) implements ChannelEntityCommand { }
    record SendMessage(Long userId, String message) implements ChannelEntityCommand { }
    record UpdateMessage(Long messageId, String updatedMessage) implements ChannelEntityCommand { }
    record DeleteMessage(Long messageId) implements ChannelEntityCommand { }
    record SyncPersistedMessage(ChatMessage message) implements ChannelEntityCommand { }
    record SyncUpdatedMessage(Long messageId, String updatedMessage) implements ChannelEntityCommand { }
    record SyncDeletedMessage(Long messageId) implements ChannelEntityCommand { }
    record ResolveHistory(long messageSequence, int size, ActorRef<ClientSessionCommand> replyTo) implements ChannelEntityCommand { }
    record RemoveShutdownReader(String readerName) implements ChannelEntityCommand { }
}
