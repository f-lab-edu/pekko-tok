package com.tok.pekko.adapter.out.websocket;

import com.tok.pekko.domain.chat.actor.ChatMessage;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Sinks;

public class WebSocketMessageSender implements ClientMessageSender {

    private final AtomicReference<Sinks.Many<WebSocketPayload>> sinkHolder;

    public WebSocketMessageSender() {
        this(null);
    }

    public WebSocketMessageSender(Sinks.Many<WebSocketPayload> sink) {
        this.sinkHolder = new AtomicReference<>(sink);
    }

    public void attachSink(Sinks.Many<WebSocketPayload> sink) {
        sinkHolder.set(sink);
    }

    public void detachSink(Sinks.Many<WebSocketPayload> sink) {
        sinkHolder.compareAndSet(sink, null);
    }

    @Override
    public void sendMessage(ChatMessage message) {
        WebSocketPayload webSocketPayload = new WebSocketPayload(WebSocketMessageType.NEW, message);

        emit(webSocketPayload);
    }

    @Override
    public void sendMessages(List<ChatMessage> messages) {
        messages.forEach(this::sendMessage);
    }

    @Override
    public void sendDeletedMessage(ChatMessage deletedMessage) {
        WebSocketPayload webSocketPayload = new WebSocketPayload(WebSocketMessageType.DELETED, deletedMessage);

        emit(webSocketPayload);
    }

    @Override
    public void sendUpdatedMessage(ChatMessage updatedMessage) {
        WebSocketPayload webSocketPayload = new WebSocketPayload(WebSocketMessageType.UPDATED, updatedMessage);

        emit(webSocketPayload);
    }

    @Override
    public void sendWebSocketPing() {
        emit(WebSocketPayload.PING_PAYLOAD);
    }

    @Override
    public void requestSessionReconnect() {
        emit(WebSocketPayload.RECONNECT_PAYLOAD);
    }

    @Override
    public void sendChangedChannelMembership(Long channelId, ChannelMembership channelMembership, int membershipCount) {
        ChannelMembershipPayload channelMembershipPayload = ChannelMembershipPayload.from(
                channelId,
                channelMembership,
                membershipCount
        );
        WebSocketPayload webSocketPayload = new WebSocketPayload(
                WebSocketMessageType.CHANNEL_MEMBERSHIP_CHANGED,
                channelMembershipPayload
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendChangedMembershipCount(Long channelId, int membershipCount) {
        ChannelMembershipCountPayload channelMembershipCountPayload = new ChannelMembershipCountPayload(channelId, membershipCount);
        WebSocketPayload webSocketPayload = new WebSocketPayload(
                WebSocketMessageType.CHANNEL_MEMBERSHIP_COUNT_CHANGED,
                channelMembershipCountPayload
        );

        emit(webSocketPayload);
    }

    @Override
    public void sendInvitedChannel(Long channelId) {
        ChannelInvitePayload channelInvitePayload = new ChannelInvitePayload(channelId);
        WebSocketPayload webSocketPayload = new WebSocketPayload(WebSocketMessageType.CHANNEL_INVITED, channelInvitePayload);

        emit(webSocketPayload);
    }

    @Override
    public void sendEditedChannelName(Long channelId, String editedName) {
        ChannelNamePayload channelNamePayload = new ChannelNamePayload(channelId, editedName);
        WebSocketPayload webSocketPayload = new WebSocketPayload(WebSocketMessageType.CHANNEL_NAME_EDITED, channelNamePayload);

        emit(webSocketPayload);
    }

    @Override
    public void sendKickedFromChannel(Long channelId) {
        ChannelKickedPayload channelKickedPayload = new ChannelKickedPayload(channelId);
        WebSocketPayload webSocketPayload = new WebSocketPayload(WebSocketMessageType.CHANNEL_KICKED, channelKickedPayload);

        emit(webSocketPayload);
    }

    @Override
    public void sendChangedChannelPolicy(Long channelId, ChannelPolicy channelPolicy) {
        ChannelPolicyPayload channelPolicyPayload = ChannelPolicyPayload.from(channelId, channelPolicy);
        WebSocketPayload webSocketPayload = new WebSocketPayload(WebSocketMessageType.CHANNEL_POLICY_CHANGED, channelPolicyPayload);

        emit(webSocketPayload);
    }

    @Override
    public void sendError(Long channelId, String reason) {
        ErrorPayload errorPayload = new ErrorPayload(channelId, reason);
        WebSocketPayload webSocketPayload = new WebSocketPayload(WebSocketMessageType.ERROR, errorPayload);

        emit(webSocketPayload);
    }

    private void emit(WebSocketPayload payload) {
        Sinks.Many<WebSocketPayload> sink = sinkHolder.get();

        if (sink != null) {
            sink.tryEmitNext(payload);
        }
    }

    public record WebSocketPayload(WebSocketMessageType type, Object message) {

        static final WebSocketPayload PING_PAYLOAD = new WebSocketPayload(WebSocketMessageType.WS_HEALTH_PING, null);
        static final WebSocketPayload RECONNECT_PAYLOAD = new WebSocketPayload(WebSocketMessageType.WS_RECONNECT, null);
    }

    public record ChannelMembershipPayload(
            Long channelId,
            Long memberId,
            String role,
            Set<ChannelPermissionType> permissions,
            int membershipCount
    ) {
        static ChannelMembershipPayload from(Long channelId, ChannelMembership channelMembership, int membershipCount) {
            return new ChannelMembershipPayload(
                    channelId,
                    channelMembership.getUserId().getValue(),
                    channelMembership.getRole().name(),
                    channelMembership.getPermissions().getAll(),
                    membershipCount
            );
        }
    }

    public record ChannelNamePayload(Long channelId, String name) { }

    public record ChannelInvitePayload(Long channelId) { }

    public record ChannelKickedPayload(Long channelId) { }

    public record ChannelMembershipCountPayload(Long channelId, int membershipCount) { }

    public record ChannelPolicyPayload(
            Long channelId,
            boolean canEditOwnMessage,
            boolean canDeleteOwnMessage,
            boolean isPublic
    ) {
        static ChannelPolicyPayload from(Long channelId, ChannelPolicy policy) {
            return new ChannelPolicyPayload(
                    channelId,
                    policy.canEditOwnMessage(),
                    policy.canDeleteOwnMessage(),
                    policy.isPublic()
            );
        }
    }

    public record ErrorPayload(Long channelId, String reason) { }
}
