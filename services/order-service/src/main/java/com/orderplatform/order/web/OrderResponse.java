package com.orderplatform.order.web;

import com.orderplatform.order.domain.Order;
import com.orderplatform.order.domain.OrderStatus;

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

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus(),
                order.getCorrelationId(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
