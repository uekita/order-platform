package com.orderplatform.qa.api.model;

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
