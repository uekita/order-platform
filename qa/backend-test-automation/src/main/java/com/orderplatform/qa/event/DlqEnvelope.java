package com.orderplatform.qa.event;

import java.time.Instant;

public record DlqEnvelope(
        String originalTopic,
        String originalKey,
        String originalPayload,
        String errorType,
        String errorMessage,
        Instant failedAt,
        String correlationId
) {
}
