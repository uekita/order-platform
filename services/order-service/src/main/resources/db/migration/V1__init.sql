CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    customer_id     UUID           NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    correlation_id  VARCHAR(128),
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_correlation_id ON orders (correlation_id);

-- Append-only projection written by the Kafka consumer.
-- The primary key on event_id is the idempotency guard for duplicate deliveries.
CREATE TABLE order_event_audit (
    event_id        UUID PRIMARY KEY,
    order_id        UUID           NOT NULL,
    event_type      VARCHAR(100)   NOT NULL,
    correlation_id  VARCHAR(128),
    payload         TEXT           NOT NULL,
    received_at     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_order_event_audit_order_id ON order_event_audit (order_id);
CREATE INDEX idx_order_event_audit_correlation_id ON order_event_audit (correlation_id);
