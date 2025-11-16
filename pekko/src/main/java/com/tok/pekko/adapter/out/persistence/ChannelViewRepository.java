package com.tok.pekko.adapter.out.persistence;

import com.tok.pekko.application.port.out.dto.ChannelDto;
import java.util.List;

public interface ChannelViewRepository {

    List<ChannelDto> findAll(Long lastChannelId, int size);
}
