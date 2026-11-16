package com.orderplatform.qa.db;

import com.orderplatform.qa.config.TestConfig;
import com.orderplatform.qa.util.Json;
import com.orderplatform.qa.util.Waits;
import io.qameta.allure.Allure;
import org.assertj.core.api.Assertions;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class DatabaseAssertions {

    private DatabaseAssertions() {
    }

    public static OrderDbRepository.OrderRow awaitOrderPersisted(TestConfig config,
                                                                 OrderDbRepository repository,
                                                                 UUID orderId) {
        AtomicReference<OrderDbRepository.OrderRow> found = new AtomicReference<>();
        try {
            Waits.await(config)
                    .alias("order " + orderId + " persisted in the database")
                    .untilAsserted(() -> {
                        Optional<OrderDbRepository.OrderRow> row = repository.findOrder(orderId);
                        Assertions.assertThat(row)
                                .as("order row %s", orderId)
                                .isPresent();
                        found.set(row.orElseThrow());
                    });
            return found.get();
        } catch (AssertionError error) {
            attach("Order query result", repository.findOrder(orderId).orElse(null));
            throw error;
        }
    }

    public static OrderDbRepository.EventAuditRow awaitEventAudit(TestConfig config,
                                                                  OrderDbRepository repository,
                                                                  UUID eventId) {
        AtomicReference<OrderDbRepository.EventAuditRow> found = new AtomicReference<>();
        try {
            Waits.await(config)
                    .alias("event " + eventId + " processed by the consumer")
                    .untilAsserted(() -> {
                        Optional<OrderDbRepository.EventAuditRow> row = repository.findEventAudit(eventId);
                        Assertions.assertThat(row)
                                .as("event audit row %s", eventId)
                                .isPresent();
                        found.set(row.orElseThrow());
                    });
            return found.get();
        } catch (AssertionError error) {
            attach("Event audit query result", repository.findEventAudit(eventId).orElse(null));
            throw error;
        }
    }

    public static void awaitEventAuditCount(TestConfig config,
                                            OrderDbRepository repository,
                                            UUID eventId,
                                            long expectedCount) {
        try {
            Waits.await(config)
                    .alias("event " + eventId + " audit count is " + expectedCount)
                    .untilAsserted(() -> Assertions.assertThat(repository.countEventAudits(eventId))
                            .as("event audit count for %s", eventId)
                            .isEqualTo(expectedCount));
        } catch (AssertionError error) {
            attach("Event audit count", repository.countEventAudits(eventId));
            throw error;
        }
    }

    private static void attach(String name, Object value) {
        try {
            String content = value == null ? "<no rows>" : Json.pretty(value);
            Allure.addAttachment(name, "application/json", content, ".json");
        } catch (RuntimeException ignored) {
            // Attachments must never hide the original assertion failure.
        }
    }
}
