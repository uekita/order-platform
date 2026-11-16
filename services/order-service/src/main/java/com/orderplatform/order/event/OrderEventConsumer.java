package com.orderplatform.order.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.config.KafkaTopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private static final String EXPECTED_TYPE = "order.created.v1";

    private final JdbcTemplate jdbcTemplate;
    private final DlqPublisher dlqPublisher;
    private final KafkaTopicProperties topics;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(JdbcTemplate jdbcTemplate,
                              DlqPublisher dlqPublisher,
                              KafkaTopicProperties topics,
                              ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.dlqPublisher = dlqPublisher;
        this.topics = topics;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOrderEvent(String payload,
                             @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            reject(key, payload, "MALFORMED_JSON", exception.getOriginalMessage(), null);
            return;
        }

        String eventType = text(event, "eventType");
        String eventIdText = text(event, "eventId");
        String correlationId = text(event, "correlationId");
        String orderIdText = event.path("data").path("orderId").asText(null);

        if (eventType == null || eventIdText == null || orderIdText == null) {
            reject(key, payload, "VALIDATION_ERROR",
                    "eventId, eventType and data.orderId are required", correlationId);
            return;
        }
        if (!EXPECTED_TYPE.equals(eventType)) {
            reject(key, payload, "UNSUPPORTED_EVENT_TYPE",
                    "Unsupported eventType: " + eventType, correlationId);
            return;
        }

        UUID eventId;
        UUID orderId;
        try {
            eventId = UUID.fromString(eventIdText);
            orderId = UUID.fromString(orderIdText);
        } catch (IllegalArgumentException exception) {
            reject(key, payload, "VALIDATION_ERROR",
                    "eventId and data.orderId must be UUIDs", correlationId);
            return;
        }

        int inserted = jdbcTemplate.update("""
                INSERT INTO order_event_audit
                    (event_id, order_id, event_type, correlation_id, payload, received_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                eventId,
                orderId,
                eventType,
                correlationId,
                payload,
                Timestamp.from(Instant.now())
        );

        if (inserted == 0) {
            log.info("Ignored duplicate eventId={} orderId={} correlationId={}",
                    eventId, orderId, correlationId);
        } else {
            log.info("Processed eventId={} orderId={} correlationId={}",
                    eventId, orderId, correlationId);
        }
    }

    private void reject(String key, String payload, String errorType, String message, String correlationId) {
        dlqPublisher.publish(key, payload, errorType, message, correlationId);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
