package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.application.port.out.ChannelMemberViewPort;
import com.tok.pekko.application.port.out.dto.ChannelMemberDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChannelMemberViewPortAdapter implements ChannelMemberViewPort {

    private final ChannelMemberViewRepository channelMemberViewRepository;

    @Override
    public List<ChannelMemberDto> findAllByChannelId(Long channelId, Long lastChannelMembershipId, int size) {
        return channelMemberViewRepository.findAllByChannelId(channelId, lastChannelMembershipId, size);
    }
}
