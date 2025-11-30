package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelInvitePayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelKickedPayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelMembershipCountPayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelMembershipPayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelNamePayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChannelPolicyPayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ChatMessagePayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.DeletedChatMessagePayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketMessagePayload.ErrorPayload;
import com.tok.pekko.adapter.out.websocket.message.WebSocketOutboundMessage;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Sinks;

public class WebSocketMessageSender implements ClientMessageSender {

    private final AtomicReference<Sinks.Many<WebSocketOutboundMessage>> sinkHolder;

    public WebSocketMessageSender() {
        this(null);
    }

    public WebSocketMessageSender(Sinks.Many<WebSocketOutboundMessage> sink) {
        this.sinkHolder = new AtomicReference<>(sink);
    }

    public void attachSink(Sinks.Many<WebSocketOutboundMessage> sink) {
        sinkHolder.set(sink);
    }

    public void detachSink(Sinks.Many<WebSocketOutboundMessage> sink) {
        sinkHolder.compareAndSet(sink, null);
    }

    @Override
    public void sendMessage(ChatMessage message) {
        WebSocketOutboundMessage webSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.NEW,
                new ChatMessagePayload(message)
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendMessages(List<ChatMessage> messages) {
        messages.forEach(this::sendMessage);
    }

    @Override
    public void sendDeletedMessage(Long deletedMessageId) {
        WebSocketOutboundMessage webSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.DELETED,
                new DeletedChatMessagePayload(deletedMessageId)
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendUpdatedMessage(ChatMessage updatedMessage) {
        WebSocketOutboundMessage webSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.UPDATED,
                new ChatMessagePayload(updatedMessage)
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendWebSocketPing() {
        emit(WebSocketOutboundMessage.PING_MESSAGE);
    }

    @Override
    public void requestSessionReconnect() {
        emit(WebSocketOutboundMessage.RECONNECT_MESSAGE);
    }

    @Override
    public void sendChangedChannelMembership(Long channelId, ChannelMembership channelMembership, int membershipCount) {
        ChannelMembershipPayload channelMembershipPayload = ChannelMembershipPayload.from(
                channelId,
                channelMembership,
                membershipCount
        );
        WebSocketOutboundMessage webSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_MEMBERSHIP_CHANGED,
                channelMembershipPayload
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendChangedMembershipCount(Long channelId, int membershipCount) {
        ChannelMembershipCountPayload channelMembershipCountPayload = new ChannelMembershipCountPayload(channelId, membershipCount);
        WebSocketOutboundMessage webSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_MEMBERSHIP_COUNT_CHANGED,
                channelMembershipCountPayload
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendInvitedChannel(Long channelId) {
        ChannelInvitePayload channelInvitePayload = new ChannelInvitePayload(channelId);
        WebSocketOutboundMessage webSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_INVITED,
                channelInvitePayload
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendEditedChannelName(Long channelId, String editedName) {
        ChannelNamePayload channelNamePayload = new ChannelNamePayload(channelId, editedName);
        WebSocketOutboundMessage webSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_NAME_EDITED,
                channelNamePayload
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendKickedFromChannel(Long channelId) {
        ChannelKickedPayload channelKickedPayload = new ChannelKickedPayload(channelId);
        WebSocketOutboundMessage webSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_KICKED,
                channelKickedPayload
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendChangedChannelPolicy(Long channelId, ChannelPolicy channelPolicy) {
        ChannelPolicyPayload channelPolicyPayload = ChannelPolicyPayload.from(channelId, channelPolicy);
        WebSocketOutboundMessage webSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.CHANNEL_POLICY_CHANGED,
                channelPolicyPayload
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendError(Long channelId, String reason) {
        ErrorPayload errorPayload = new ErrorPayload(channelId, reason);
        WebSocketOutboundMessage webSocketPayload = new WebSocketOutboundMessage(
                WebSocketMessageType.ERROR,
                errorPayload
        );

        emit(webSocketPayload);
    }

    private void emit(WebSocketOutboundMessage payload) {
        Sinks.Many<WebSocketOutboundMessage> sink = sinkHolder.get();

        if (sink != null) {
            sink.tryEmitNext(payload);
        }
    }
}
