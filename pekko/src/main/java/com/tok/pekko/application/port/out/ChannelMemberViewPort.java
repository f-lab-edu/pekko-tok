package com.tok.pekko.application.port.out;

import com.tok.pekko.application.port.out.dto.ChannelMemberDto;
import java.util.List;

public interface ChannelMemberViewPort {

    List<ChannelMemberDto> findAllByChannelId(Long channelId, Long lastChannelMembershipId, int size);
}
