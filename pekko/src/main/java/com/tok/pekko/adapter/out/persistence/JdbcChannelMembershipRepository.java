package com.tok.pekko.adapter.out.persistence;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JdbcChannelMembershipRepository implements ChannelMembershipRepository {

    @Override
    public List<Long> findAllIChannelIds(Long userId) {
        // NO-OP
        return List.of();
    }

    @Override
    public void save(Long userId, Long channelId) {
        // NO-OP
    }

    @Override
    public void delete(Long userId, Long channelId) {
        // NO-OP
    }
}
