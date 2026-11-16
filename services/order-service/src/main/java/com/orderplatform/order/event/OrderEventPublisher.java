package com.orderplatform.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orderplatform.order.config.KafkaTopicProperties;
import com.orderplatform.order.domain.Order;
import com.orderplatform.order.service.EventPublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String EVENT_TYPE = "order.created.v1";
    private static final int EVENT_VERSION = 1;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicProperties topics;
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               KafkaTopicProperties topics,
                               ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes synchronously so a broker failure surfaces as a 503 instead of a silent
     * loss. A production service would normally use a transactional outbox here.
     */
    public void publishOrderCreated(Order order, String correlationId) {
        UUID eventId = UUID.randomUUID();
        ObjectNode event = objectMapper.createObjectNode();
        event.put("eventId", eventId.toString());
        event.put("eventType", EVENT_TYPE);
        event.put("eventVersion", EVENT_VERSION);
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", correlationId);

        ObjectNode data = event.putObject("data");
        data.put("orderId", order.getId().toString());
        data.put("customerId", order.getCustomerId().toString());
        data.put("amount", order.getAmount());
        data.put("currency", order.getCurrency());
        data.put("status", order.getStatus().name());

        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topics.orderEvents(), order.getId().toString(), payload)
                    .get(5, TimeUnit.SECONDS);
            log.info("Published {} eventId={} orderId={} correlationId={}",
                    EVENT_TYPE, eventId, order.getId(), correlationId);
        } catch (Exception exception) {
            throw new EventPublishException(
                    "Could not publish order.created event for order " + order.getId(), exception);
        }
    }
}
