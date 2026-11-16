package com.orderplatform.qa.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orderplatform.qa.util.Json;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds raw JSON event payloads. Tests deliberately bypass the application's
 * publisher so they can inject duplicates, malformed JSON and invalid schemas.
 */
public final class EventPayloads {

    public static final String ORDER_CREATED = "order.created.v1";

    private EventPayloads() {
    }

    public static String orderCreated(UUID eventId, UUID orderId, String correlationId) {
        return orderCreated(
                eventId,
                orderId,
                UUID.randomUUID(),
                BigDecimal.valueOf(ThreadLocalRandom.current().nextLong(100, 100_000), 2),
                "CAD",
                correlationId
        );
    }

    public static String orderCreated(UUID eventId,
                                      UUID orderId,
                                      UUID customerId,
                                      BigDecimal amount,
                                      String currency,
                                      String correlationId) {
        ObjectMapper mapper = Json.mapper();
        ObjectNode event = mapper.createObjectNode();
        event.put("eventId", eventId.toString());
        event.put("eventType", ORDER_CREATED);
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", correlationId);

        ObjectNode data = event.putObject("data");
        data.put("orderId", orderId.toString());
        data.put("customerId", customerId.toString());
        data.put("amount", amount);
        data.put("currency", currency);
        data.put("status", "CREATED");
        return Json.write(event);
    }

    /**
     * Contains a stable marker so the DLQ assertion can find this exact message
     * even though the payload cannot be parsed into a typed event.
     */
    public static String malformedJson(String marker) {
        return "{\"eventId\":\"" + marker + "\",\"eventType\":";
    }

    public static String missingRequiredFields(String correlationId) {
        return "{\"eventType\":\"" + ORDER_CREATED + "\",\"correlationId\":\"" + correlationId + "\"}";
    }

    public static String unsupportedEventType(UUID eventId, UUID orderId, String correlationId) {
        ObjectNode event = Json.mapper().createObjectNode();
        event.put("eventId", eventId.toString());
        event.put("eventType", "order.cancelled.v99");
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", correlationId);
        event.putObject("data").put("orderId", orderId.toString());
        return Json.write(event);
    }

    public static String invalidUuidFields(String eventId, String orderId, String correlationId) {
        ObjectNode event = Json.mapper().createObjectNode();
        event.put("eventId", eventId);
        event.put("eventType", ORDER_CREATED);
        event.put("eventVersion", 1);
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", correlationId);
        event.putObject("data").put("orderId", orderId);
        return Json.write(event);
    }
}
