package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.common.CborSerializable;
import com.tok.pekko.domain.chat.model.ChatMessage;
import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.domain.chat.port.in.NodeManagerProtocol.NodeManagerActorCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChatChannelProtocol {

    interface ChatChannelEntityCommand extends CborSerializable { }

    record SyncRecentMessages(List<ChatMessage> messages) implements ChatChannelEntityCommand { }
    record RequestJoin(Long userId, ActorRef<ClientSessionCommand> clientRef, ActorRef<NodeManagerActorCommand> replyTo) implements ChatChannelEntityCommand { }
    record RegisterReader(Long userId, ActorRef<ChatChannelReaderCommand> reader) implements ChatChannelEntityCommand { }
    record SendMessageCommand(Long userId, String message, LocalDateTime timestamp) implements ChatChannelEntityCommand { }
    record SyncPersistedMessage(ChatMessage message) implements ChatChannelEntityCommand { }
    record RemoveShutdownReader(Long userId) implements ChatChannelEntityCommand { }
}
