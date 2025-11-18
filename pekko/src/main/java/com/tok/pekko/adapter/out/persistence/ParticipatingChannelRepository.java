package com.tok.pekko.adapter.out.persistence;

import java.util.List;

public interface ParticipatingChannelRepository {

    List<Long> findAllChannelIds(Long userId);
}
