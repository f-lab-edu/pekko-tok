package com.tok.pekko.adapter.out.websocket.message;

import com.fasterxml.jackson.annotation.JsonValue;
import com.tok.pekko.domain.channel.model.ChannelMembership;
import com.tok.pekko.domain.channel.model.ChannelPermissionType;
import com.tok.pekko.domain.channel.model.vo.ChannelPolicy;
import com.tok.pekko.domain.chat.actor.ChatMessage;
import java.util.Set;

public interface WebSocketMessagePayload {

    enum WebSocketEmptyPayload implements WebSocketMessagePayload {
        INSTANCE;

        @JsonValue
        public Object value() {
            return null;
        }
    }

    record ChannelInvitePayload(Long channelId) implements WebSocketMessagePayload { }
    record ChannelKickedPayload(Long channelId) implements WebSocketMessagePayload { }
    record ChannelMembershipCountPayload(Long channelId, int membershipCount) implements WebSocketMessagePayload { }
    record ChannelNamePayload(Long channelId, String name) implements WebSocketMessagePayload { }
    record ChatMessagePayload(ChatMessage message) implements WebSocketMessagePayload { }
    record ErrorPayload(Long channelId, String reason) implements WebSocketMessagePayload { }

    record ChannelMembershipPayload(
            Long channelId,
            Long memberId,
            String role,
            Set<ChannelPermissionType> permissions,
            int membershipCount
    ) implements WebSocketMessagePayload {

        public static ChannelMembershipPayload from(
                Long channelId,
                ChannelMembership channelMembership,
                int membershipCount
        ) {
            return new ChannelMembershipPayload(
                    channelId,
                    channelMembership.getUserId().getValue(),
                    channelMembership.getRole().name(),
                    channelMembership.getPermissions().getAll(),
                    membershipCount
            );
        }
    }

    record ChannelPolicyPayload(
            Long channelId,
            boolean canEditOwnMessage,
            boolean canDeleteOwnMessage,
            boolean isPublic
    ) implements WebSocketMessagePayload {

        public static ChannelPolicyPayload from(Long channelId, ChannelPolicy policy) {
            return new ChannelPolicyPayload(
                    channelId,
                    policy.canEditOwnMessage(),
                    policy.canDeleteOwnMessage(),
                    policy.isPublic()
            );
        }
    }
}
