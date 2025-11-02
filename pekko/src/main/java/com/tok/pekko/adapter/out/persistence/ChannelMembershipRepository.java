package com.tok.pekko.adapter.out.persistence;

import java.util.List;

public interface ChannelMembershipRepository {

    List<Long> findAllIChannelIds(Long userId);

    void save(Long userId, Long channelId);

    void delete(Long userId, Long channelId);
}
