package com.tok.pekko.domain.channel.model.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ChannelId {

    public static final ChannelId EMPTY_CHANNEL_ID = new ChannelId(null);

    private final Long value;

    public static ChannelId create(Long value) {
        validateValue(value);

        return new ChannelId(value);
    }

    private static void validateValue(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("채널 ID는 양수여야 합니다.");
        }
    }

    private ChannelId(Long value) {
        this.value = value;
    }

    public boolean isEqualId(Long id) {
        return this.value.equals(id);
    }
}
