package com.orderplatform.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orderplatform.order.config.KafkaTopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(DlqPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicProperties topics;
    private final ObjectMapper objectMapper;

    public DlqPublisher(KafkaTemplate<String, String> kafkaTemplate,
                        KafkaTopicProperties topics,
                        ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.objectMapper = objectMapper;
    }

    public void publish(String originalKey,
                        String originalPayload,
                        String errorType,
                        String errorMessage,
                        String correlationId) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("originalTopic", topics.orderEvents());
        envelope.put("originalKey", originalKey);
        envelope.put("originalPayload", originalPayload);
        envelope.put("errorType", errorType);
        envelope.put("errorMessage", truncate(errorMessage));
        envelope.put("failedAt", Instant.now().toString());
        envelope.put("correlationId", correlationId);

        String key = originalKey == null || originalKey.isBlank()
                ? UUID.randomUUID().toString()
                : originalKey;

        try {
            kafkaTemplate.send(topics.orderEventsDlq(), key, objectMapper.writeValueAsString(envelope))
                    .get(5, TimeUnit.SECONDS);
            log.warn("Published rejected event to DLQ topic={} errorType={} key={}",
                    topics.orderEventsDlq(), errorType, key);
        } catch (Exception exception) {
            // Do not throw: throwing would put the original record into an endless retry loop.
            log.error("Failed to publish rejected event to DLQ topic={} errorType={} key={}",
                    topics.orderEventsDlq(), errorType, key, exception);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
