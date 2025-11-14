package com.tok.pekko.adapter.out.persistence;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JdbcParticipatingChannelRepository implements ParticipatingChannelRepository {

    @Override
    public List<Long> findAllChannelIds(Long userId) {
        // NO-OP
        return List.of();
    }
}
