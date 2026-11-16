package com.orderplatform.order.web;

import java.util.UUID;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";

    private CorrelationId() {
    }

    public static String from(String candidate) {
        return candidate == null || candidate.isBlank()
                ? UUID.randomUUID().toString()
                : candidate.trim();
    }
}
