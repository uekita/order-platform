package com.orderplatform.qa.event;

import com.orderplatform.qa.api.client.ApiResponse;
import com.orderplatform.qa.api.client.ContractAssertions;
import com.orderplatform.qa.base.BaseEventTest;
import com.orderplatform.qa.db.DatabaseAssertions;
import com.orderplatform.qa.db.OrderDbRepository;
import com.orderplatform.qa.tags.EventTest;
import com.orderplatform.qa.tags.LocalOnly;
import com.orderplatform.qa.tags.RegressionTest;
import com.orderplatform.qa.util.Json;
import com.orderplatform.qa.util.TestIds;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Order Platform Backend")
@Feature("Event-driven resilience")
@Story("Injected Kafka events: duplicates, malformed JSON, invalid schemas")
@Owner("backend-qa")
@RegressionTest
@EventTest
@LocalOnly
class EventDrivenLocalTest extends BaseEventTest {

    @Test
    @DisplayName("A valid injected event is projected exactly once")
    @Severity(SeverityLevel.CRITICAL)
    void shouldProcessValidEvent() {
        UUID eventId = TestIds.uuid();
        UUID orderId = TestIds.uuid();
        String payload = EventPayloads.orderCreated(eventId, orderId, correlationId);

        kafka.publish(config.kafkaOrderEventsTopic(), eventId.toString(), payload);

        OrderDbRepository.EventAuditRow audit = DatabaseAssertions.awaitEventAudit(config, db, eventId);
        assertThat(audit.orderId()).isEqualTo(orderId);
        assertThat(audit.eventType()).isEqualTo(EventPayloads.ORDER_CREATED);
        assertThat(audit.correlationId()).isEqualTo(correlationId);
        assertThat(Json.readTree(audit.payload()).path("eventId").asText()).isEqualTo(eventId.toString());
    }

    @Test
    @DisplayName("A duplicate event id is idempotent and does not block subsequent events")
    @Severity(SeverityLevel.BLOCKER)
    void shouldIgnoreDuplicateEvent() {
        UUID eventId = TestIds.uuid();
        UUID orderId = TestIds.uuid();
        String partitionKey = eventId.toString();
        String payload = EventPayloads.orderCreated(eventId, orderId, correlationId);

        kafka.publish(config.kafkaOrderEventsTopic(), partitionKey, payload);
        DatabaseAssertions.awaitEventAudit(config, db, eventId);

        // Replay the exact same event, then publish a distinct follow-up event on the same
        // partition key. Observing the follow-up event in the DB proves the consumer moved
        // past the duplicate cleanly without crashing or stalling.
        kafka.publish(config.kafkaOrderEventsTopic(), partitionKey, payload);
        UUID followUpEventId = TestIds.uuid();
        UUID followUpOrderId = TestIds.uuid();
        kafka.publish(config.kafkaOrderEventsTopic(), partitionKey,
                EventPayloads.orderCreated(followUpEventId, followUpOrderId, correlationId));

        DatabaseAssertions.awaitEventAudit(config, db, followUpEventId);
        assertThat(db.countEventAudits(eventId)).isEqualTo(1);
        assertThat(db.countEventAuditsByOrderId(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Malformed JSON is routed to the DLQ and the service stays healthy")
    @Severity(SeverityLevel.CRITICAL)
    void shouldRouteMalformedJsonToDlq() {
        String marker = "malformed-" + TestIds.uuid();

        try (KafkaEventConsumer dlq = dlqConsumer()) {
            kafka.publish(config.kafkaOrderEventsTopic(), marker, EventPayloads.malformedJson(marker));

            DlqEnvelope envelope = awaitDlqEnvelope(
                    dlq,
                    event -> marker.equals(event.key()) || event.value().contains(marker),
                    "marker " + marker);
            assertThat(envelope.errorType()).isEqualTo("MALFORMED_JSON");
            assertThat(envelope.originalPayload()).contains(marker);
        }

        ApiResponse healthResponse = health.health();
        assertThat(healthResponse.status()).isEqualTo(200);
        assertThat(healthResponse.json().path("status").asText()).isEqualTo("UP");
    }

    @Test
    @DisplayName("An event with missing required fields is routed to the DLQ")
    void shouldRouteInvalidSchemaToDlq() {
        String key = TestIds.uuid().toString();

        try (KafkaEventConsumer dlq = dlqConsumer()) {
            kafka.publish(config.kafkaOrderEventsTopic(), key,
                    EventPayloads.missingRequiredFields(correlationId));

            DlqEnvelope envelope = awaitDlqEnvelope(dlq, event -> key.equals(event.key()), "key " + key);
            assertThat(envelope.errorType()).isEqualTo("VALIDATION_ERROR");
            assertThat(envelope.correlationId()).isEqualTo(correlationId);
        }
    }

    @Test
    @DisplayName("An event with non-UUID identifiers is routed to the DLQ")
    void shouldRouteInvalidUuidToDlq() {
        String key = TestIds.uuid().toString();

        try (KafkaEventConsumer dlq = dlqConsumer()) {
            kafka.publish(config.kafkaOrderEventsTopic(), key,
                    EventPayloads.invalidUuidFields("not-a-uuid", TestIds.uuid().toString(), correlationId));

            DlqEnvelope envelope = awaitDlqEnvelope(dlq, event -> key.equals(event.key()), "key " + key);
            assertThat(envelope.errorType()).isEqualTo("VALIDATION_ERROR");
            assertThat(envelope.correlationId()).isEqualTo(correlationId);
        }
    }

    @Test
    @DisplayName("An unsupported event type is routed to the DLQ")
    void shouldRouteUnsupportedEventTypeToDlq() {
        UUID eventId = TestIds.uuid();
        UUID orderId = TestIds.uuid();

        try (KafkaEventConsumer dlq = dlqConsumer()) {
            kafka.publish(config.kafkaOrderEventsTopic(), eventId.toString(),
                    EventPayloads.unsupportedEventType(eventId, orderId, correlationId));

            DlqEnvelope envelope = awaitDlqEnvelope(
                    dlq,
                    event -> eventId.toString().equals(event.key()),
                    "event " + eventId);
            assertThat(envelope.errorType()).isEqualTo("UNSUPPORTED_EVENT_TYPE");
        }
    }

    private DlqEnvelope awaitDlqEnvelope(KafkaEventConsumer dlq,
                                         Predicate<ConsumedEvent> predicate,
                                         String description) {
        ConsumedEvent consumed = dlq.awaitMatching(predicate, eventTimeout())
                .orElseThrow(() -> new AssertionError("No DLQ message found for " + description));
        ContractAssertions.assertMatchesSchema(consumed.value(), "schemas/dlq-envelope.schema.json");
        return Json.read(consumed.value(), DlqEnvelope.class);
    }
}
