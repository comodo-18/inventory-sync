package com.anurag.inventory_sync.event;

import java.time.Instant;

public record CacheInvalidationEvent(
        Long productId,
        String eventType,
        Instant timestamp
) {}
