package com.orderplatform.qa.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.orderplatform.qa.api.client.ApiResponse;
import com.orderplatform.qa.api.client.ContractAssertions;
import com.orderplatform.qa.api.model.CreateOrderRequest;
import com.orderplatform.qa.api.model.OrderResponse;
import com.orderplatform.qa.base.BaseEventTest;
import com.orderplatform.qa.db.DatabaseAssertions;
import com.orderplatform.qa.db.OrderDbRepository;
import com.orderplatform.qa.tags.E2ETest;
import com.orderplatform.qa.tags.EventTest;
import com.orderplatform.qa.tags.LocalOnly;
import com.orderplatform.qa.tags.RegressionTest;
import com.orderplatform.qa.util.Json;
import com.orderplatform.qa.util.TestDataFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Order Platform Backend")
@Feature("Event-driven order flow")
@Story("API -> Kafka -> consumer -> PostgreSQL")
@Owner("backend-qa")
@E2ETest
@RegressionTest
@EventTest
@LocalOnly
class OrderEventE2ETest extends BaseEventTest {

    @Test
    @DisplayName("Creating an order publishes a valid event that the consumer projects into the database")
    @Severity(SeverityLevel.BLOCKER)
    void shouldPublishAndProjectOrderCreatedEvent() {
        try (KafkaEventConsumer events = eventsConsumer()) {
            CreateOrderRequest request = TestDataFactory.newOrder();
            ApiResponse created = orders.create(request);

            assertThat(created.status()).isEqualTo(201);
            OrderResponse order = created.as(OrderResponse.class);

            ConsumedEvent consumed = events
                    .awaitMatching(event -> event.value().contains(order.id().toString()), eventTimeout())
                    .orElseThrow(() -> new AssertionError(
                            "No order.created event for order " + order.id()
                                    + " on topic " + config.kafkaOrderEventsTopic()));

            ContractAssertions.assertMatchesSchema(consumed.value(), "schemas/order-created-event.schema.json");
            JsonNode event = Json.readTree(consumed.value());
            assertThat(event.path("eventType").asText()).isEqualTo(EventPayloads.ORDER_CREATED);
            assertThat(event.path("correlationId").asText()).isEqualTo(correlationId);
            assertThat(event.path("data").path("orderId").asText()).isEqualTo(order.id().toString());
            assertThat(event.path("data").path("amount").decimalValue())
                    .isEqualByComparingTo(request.amount());

            UUID eventId = UUID.fromString(event.path("eventId").asText());
            OrderDbRepository.EventAuditRow audit = DatabaseAssertions.awaitEventAudit(config, db, eventId);
            assertThat(audit.orderId()).isEqualTo(order.id());
            assertThat(audit.eventType()).isEqualTo(EventPayloads.ORDER_CREATED);
            assertThat(audit.correlationId()).isEqualTo(correlationId);
            assertThat(Json.readTree(audit.payload()).path("eventId").asText()).isEqualTo(eventId.toString());
        }
    }
}
