package com.orderplatform.qa.db;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class OrderDbRepository {

    private final Database database;

    public OrderDbRepository(Database database) {
        this.database = database;
    }

    public Optional<OrderRow> findOrder(UUID orderId) {
        return database.queryOne("""
                        SELECT id, customer_id, amount, currency, status, correlation_id,
                               created_at, updated_at
                        FROM orders
                        WHERE id = ?
                        """,
                resultSet -> new OrderRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("customer_id", UUID.class),
                        resultSet.getBigDecimal("amount"),
                        resultSet.getString("currency"),
                        resultSet.getString("status"),
                        resultSet.getString("correlation_id"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()
                ),
                orderId
        );
    }

    public long countOrdersByCorrelationId(String correlationId) {
        return database.count("SELECT count(*) FROM orders WHERE correlation_id = ?", correlationId);
    }

    public Optional<EventAuditRow> findEventAudit(UUID eventId) {
        return database.queryOne("""
                        SELECT event_id, order_id, event_type, correlation_id, payload, received_at
                        FROM order_event_audit
                        WHERE event_id = ?
                        """,
                resultSet -> new EventAuditRow(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getObject("order_id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getString("correlation_id"),
                        resultSet.getString("payload"),
                        resultSet.getTimestamp("received_at").toInstant()
                ),
                eventId
        );
    }

    public long countEventAudits(UUID eventId) {
        return database.count("SELECT count(*) FROM order_event_audit WHERE event_id = ?", eventId);
    }

    public long countEventAuditsByOrderId(UUID orderId) {
        return database.count("SELECT count(*) FROM order_event_audit WHERE order_id = ?", orderId);
    }

    public record OrderRow(
            UUID id,
            UUID customerId,
            BigDecimal amount,
            String currency,
            String status,
            String correlationId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record EventAuditRow(
            UUID eventId,
            UUID orderId,
            String eventType,
            String correlationId,
            String payload,
            Instant receivedAt
    ) {
    }
}
