package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChatChannelProtocol {

    interface ChatChannelEntityCommand extends CborSerializable { }

    record SyncRecentMessages(List<ChatMessage> messages) implements ChatChannelEntityCommand { }
    record RegisterReader(Long userId, ActorRef<ChatChannelReaderCommand> reader) implements ChatChannelEntityCommand { }
    record SendMessage(Long userId, String message) implements ChatChannelEntityCommand { }
    record UpdateMessage(Long messageId, String updatedMessage) implements ChatChannelEntityCommand { }
    record DeleteMessage(Long messageId) implements ChatChannelEntityCommand { }
    record SyncPersistedMessage(ChatMessage message) implements ChatChannelEntityCommand { }
    record SyncUpdatedMessage(Long messageId, String updatedMessage) implements ChatChannelEntityCommand { }
    record SyncDeletedMessage(Long messageId) implements ChatChannelEntityCommand { }
    record HistoryFound(List<ChatMessage> history, ActorRef<ChatChannelReaderCommand> replyTo) implements ChatChannelEntityCommand { }
    record RemoveShutdownReader(Long userId) implements ChatChannelEntityCommand { }
}
