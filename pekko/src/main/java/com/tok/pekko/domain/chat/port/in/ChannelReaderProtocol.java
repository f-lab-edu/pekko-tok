package com.tok.pekko.domain.chat.port.in;

import com.tok.pekko.domain.chat.port.out.ClientSessionProtocol.ClientSessionCommand;
import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import java.time.LocalDateTime;
import org.apache.pekko.actor.typed.ActorRef;

public interface ChannelReaderProtocol {

    interface ChannelReaderCommand extends CborSerializable { }

    record SyncNewMessage(ChatMessage message) implements ChannelReaderCommand { }
    record SyncDeletion(Long messageId) implements ChannelReaderCommand { }
    record SyncUpdate(Long messageId, String updatedMessage, LocalDateTime updatedAt) implements ChannelReaderCommand { }
    record GetHistory(long messageSequence, int size, ActorRef<ClientSessionCommand> replyTo) implements ChannelReaderCommand { }
    record RegisterClientSession(Long userId, ActorRef<ClientSessionCommand> clientSession) implements ChannelReaderCommand { }
    record UnregisterClientSession(Long userId) implements ChannelReaderCommand { }
    record PongHealthCheck(Long userId) implements ChannelReaderCommand { }
}
