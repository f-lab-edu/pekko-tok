package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;

public interface ClientMessageSender {

    void sendMessage(ChatMessage message);

    void sendMessages(List<ChatMessage> messages);

    void sendUpdatedMessage(Long messageId, String updatedMessage, LocalDateTime updatedAt);

    void sendDeletedMessage(Long deletedMessageId);

    void sendWebSocketPing();

    void requestSessionReconnect();

    void sendChangedChannelMembership(Long channelId, ChannelMembership channelMembership, int membershipCount);

    void sendChangedMembershipCount(Long channelId, int membershipCount);

    void sendInvitedChannel(Long channelId);

    void sendEditedChannelName(Long channelId, String editedName);

    void sendKickedFromChannel(Long channelId);

    void sendChannelDeleted(Long channelId);

    void sendChangedChannelPolicy(Long channelId, ChannelPolicy channelPolicy);

    void sendError(Long channelId, String reason);
}
