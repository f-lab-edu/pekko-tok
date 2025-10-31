package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.domain.chat.port.out.ChannelReaderRegistryProtocol.ChannelReaderRegistryCommand;
import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.model.ChatMessage;
import java.time.LocalDateTime;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChatChannelReaderProtocol {

    interface ChatChannelReaderCommand extends CborSerializable { }

    record SyncNewMessage(ChatMessage message) implements ChatChannelReaderCommand { }
    record SyncDeletion(Long messageId) implements ChatChannelReaderCommand { }
    record SyncUpdate(Long messageId, String updatedMessage, LocalDateTime updatedAt) implements ChatChannelReaderCommand { }
    record RequestHistory(long messageSequence, int size) implements ChatChannelReaderCommand { }
    record PingHealthCheckFromRegistry(ActorRef<ChannelReaderRegistryCommand> replyTo) implements ChatChannelReaderCommand { }
    record RegisterClientSession(Long userId, ActorRef<ClientSessionCommand> clientSession) implements ChatChannelReaderCommand { }
    record UnregisterClientSession(Long userId) implements ChatChannelReaderCommand { }
    record PingHealthCheckFromClientSession(ActorRef<ClientSessionCommand> replyTo) implements ChatChannelReaderCommand { }
    record Shutdown() implements ChatChannelReaderCommand { }
}
