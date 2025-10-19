package com.tok.pekko.domain.chat.model;

import com.tok.pekko.global.common.ActorThreadSafe;
import java.time.Clock;

public class SnowflakeSequenceGenerator {

    // Custom Epoch (2025-01-01T00:00:00Z)
    private static final long CUSTOM_EPOCH = 1735689600000L;

    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_CHANNEL_ID = (1L << NODE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private static final int TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS;
    private static final int NODE_ID_SHIFT = SEQUENCE_BITS;

    private final long channelId;
    private final Clock clock;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeSequenceGenerator(long channelId) {
        this(channelId, Clock.systemUTC());
    }

    public SnowflakeSequenceGenerator(long channelId, Clock clock) {
        if (channelId < 0) {
            throw new IllegalArgumentException("채널 식별자는 양수여야 합니다.");
        }

        this.channelId = channelId & MAX_CHANNEL_ID;
        this.clock = clock;
    }

    @ActorThreadSafe
    public long nextSequence() {
        long currentTimestamp = clock.millis();

        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            throw new IllegalStateException(
                    String.format(
                            "시계가 %dms 역행했습니다. 마지막: %d, 현재: %d",
                            offset,
                            lastTimestamp,
                            currentTimestamp
                    )
            );
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;

            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
                | (channelId << NODE_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = clock.millis();

        while (timestamp <= lastTimestamp) {
            timestamp = clock.millis();
        }

        return timestamp;
    }

    public static long getTimestampFromId(long id) {
        return (id >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH;
    }

    public static long getNodeIdFromId(long id) {
        return (id >> NODE_ID_SHIFT) & MAX_CHANNEL_ID;
    }

    public static long getSequenceFromId(long id) {
        return id & MAX_SEQUENCE;
    }
}
