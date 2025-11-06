package com.tok.pekko.domain.channel.model.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ChannelMembershipId {

    public static final ChannelMembershipId EMPTY_CHANNEL_MEMBERSHIP_ID = new ChannelMembershipId(null);

    private final Long value;

    public static ChannelMembershipId create(Long value) {
        validateValue(value);

        return new ChannelMembershipId(value);
    }

    private static void validateValue(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("채널 참여자 ID는 양수여야 합니다.");
        }
    }

    private ChannelMembershipId(Long value) {
        this.value = value;
    }

    public boolean isEqualId(Long id) {
        return this.value.equals(id);
    }
}
