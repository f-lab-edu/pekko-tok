package com.tok.pekko.application.port.out;

import com.tok.pekko.application.port.out.dto.ChannelDto;
import java.util.List;

public interface ChannelViewPort {

    List<ChannelDto> findPublicChannel(Long lastChannelId, int size);
}
