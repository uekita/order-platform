package com.orderplatform.qa.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Run-scoped identifiers. Every test gets its own correlation id, and every run
 * gets a unique prefix so parallel runs can share a staging environment safely.
 */
public final class TestIds {

    private static final String RUN_ID = resolveRunId();

    private TestIds() {
    }

    public static String runId() {
        return RUN_ID;
    }

    public static String correlationId() {
        return RUN_ID + "-" + UUID.randomUUID();
    }

    public static UUID uuid() {
        return UUID.randomUUID();
    }

    private static String resolveRunId() {
        String configured = System.getenv("TEST_RUN_ID");
        if (configured != null && !configured.isBlank()) {
            return sanitize(configured);
        }
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        return timestamp + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String sanitize(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return sanitized.length() <= 24 ? sanitized : sanitized.substring(0, 24);
    }
}
