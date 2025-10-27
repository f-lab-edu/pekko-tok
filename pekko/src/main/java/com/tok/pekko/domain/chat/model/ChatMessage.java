package com.tok.pekko.domain.chat.model;

import java.time.LocalDateTime;

public record ChatMessage(
        Long messageId,
        Long channelId,
        Long userId,
        long orderSequence,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ChatMessage create(
            Long channelId,
            Long userId,
            long messageSequence,
            String message,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new ChatMessage(null, channelId, userId, messageSequence, message, createdAt, updatedAt);
    }

    public ChatMessage {
        if (channelId == null || channelId <= 0) {
            throw new IllegalArgumentException("채널 ID는 양수여야 합니다.");
        }

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다.");
        }

        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("메시지는 비어 있을 수 없습니다.");
        }
    }

    public ChatMessage updateMessage(String message, LocalDateTime updatedAt) {
        return new ChatMessage(
                this.messageId,
                this.channelId,
                this.userId,
                this.orderSequence,
                message,
                this.createdAt,
                updatedAt
        );
    }
}
