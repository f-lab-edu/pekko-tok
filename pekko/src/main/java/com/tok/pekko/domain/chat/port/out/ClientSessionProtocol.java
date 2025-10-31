package com.tok.pekko.domain.chat.port.out;

import com.tok.pekko.global.common.CborSerializable;
import com.tok.pekko.domain.chat.model.ChatMessage;
import java.util.List;

public interface ClientSessionProtocol {

    interface ClientSessionCommand extends CborSerializable { }

    record DeliverNewMessage(ChatMessage message) implements ClientSessionCommand { }
    record DeliverUpdatedMessage(ChatMessage updatedMessage) implements ClientSessionCommand { }
    record DeliverDeletedMessage(ChatMessage deletedMessage) implements ClientSessionCommand { }
    record DeliverHistory(List<ChatMessage> messages) implements ClientSessionCommand { }
    record FoundRegisteredChannelIds(List<Long> channelIds) implements ClientSessionCommand { }
    record JoinChannel(Long channelId) implements ClientSessionCommand { }
    record SyncJoinChannel(Long channelId) implements ClientSessionCommand { }
    record LeaveChannel(Long channelId) implements ClientSessionCommand { }
    record SyncLeaveChannel(Long channelId) implements ClientSessionCommand { }
    record PongHealthCheck(Long channelId) implements ClientSessionCommand { }
    record Shutdown() implements ClientSessionCommand { }
}
