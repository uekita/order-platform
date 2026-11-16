package com.orderplatform.qa.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        BigDecimal amount,
        String currency,
        OrderStatus status,
        String correlationId,
        Instant createdAt,
        Instant updatedAt
) {
}
