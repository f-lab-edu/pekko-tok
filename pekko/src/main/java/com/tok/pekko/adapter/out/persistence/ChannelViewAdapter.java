package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.application.port.out.ChannelViewPort;
import com.tok.pekko.application.port.out.dto.ChannelDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChannelViewAdapter implements ChannelViewPort {

    private final ChannelViewRepository channelViewRepository;

    @Override
    public List<ChannelDto> findPublicChannel(Long lastChannelId, int size) {
        return channelViewRepository.findAll(lastChannelId, size);
    }
}
