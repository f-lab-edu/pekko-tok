package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.domain.chat.port.in.ChatChannelReaderProtocol.ChatChannelReaderCommand;
import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public interface ClientSessionProtocol {

    interface ClientSessionCommand extends CborSerializable { }

    record DeliverNewMessage(ChatMessage message) implements ClientSessionCommand { }
    record DeliverUpdatedMessage(ChatMessage updatedMessage) implements ClientSessionCommand { }
    record DeliverDeletedMessage(ChatMessage deletedMessage) implements ClientSessionCommand { }
    record RequestHistory(Long channelId, long messageSequence, int size) implements ClientSessionCommand { }
    record FoundHistory(List<ChatMessage> history) implements ClientSessionCommand { }
    record DeliverHistory(Long channelId, long messageSequence, int size, List<ChatMessage> history) implements ClientSessionCommand { }
    record FoundRegisteredChannelIds(List<Long> channelIds) implements ClientSessionCommand { }
    record JoinChannel(Long channelId) implements ClientSessionCommand { }
    record SyncJoinChannel(Long channelId) implements ClientSessionCommand { }
    record LeaveChannel(Long channelId) implements ClientSessionCommand { }
    record SyncLeaveChannel(Long channelId) implements ClientSessionCommand { }
    record PongHealthCheck(Long channelId) implements ClientSessionCommand { }
    record PingHealthCheck(ActorRef<ChatChannelReaderCommand> replyTo) implements ClientSessionCommand { }
    record Shutdown() implements ClientSessionCommand { }
}
