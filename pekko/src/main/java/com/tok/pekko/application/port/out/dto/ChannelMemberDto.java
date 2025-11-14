package com.tok.pekko.application.port.out.dto;

import com.tok.pekko.domain.channel.model.ChannelRole;
import java.time.LocalDateTime;

public record ChannelMemberDto(
        Long channelMembershipId,
        Long userId,
        ChannelRole channelRole,
        LocalDateTime joinedAt
) {
}
