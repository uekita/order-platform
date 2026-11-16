package com.orderplatform.order.web;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        Map<String, Object> details,
        Instant timestamp,
        String correlationId
) {
}
