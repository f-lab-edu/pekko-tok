package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.application.port.out.dto.ChannelMemberDto;
import java.util.List;

public interface ChannelMemberViewRepository {

    List<ChannelMemberDto> findAllByChannelId(Long channelId, Long lastChannelMembershipId, int size);
}
