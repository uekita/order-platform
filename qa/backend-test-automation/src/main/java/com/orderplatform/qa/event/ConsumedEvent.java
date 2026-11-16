package com.orderplatform.qa.event;

import java.time.Instant;

public record ConsumedEvent(
        String topic,
        int partition,
        long offset,
        String key,
        String value,
        Instant timestamp
) {
}
