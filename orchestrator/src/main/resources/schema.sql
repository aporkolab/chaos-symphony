-- Saga Instance Table for tracking order saga state
CREATE TABLE IF NOT EXISTS saga_instance (
    order_id VARCHAR(36) PRIMARY KEY,
    state VARCHAR(32) NOT NULL,
    failed_state VARCHAR(32),
    payment_id VARCHAR(36),
    inventory_reservation_id VARCHAR(36),
    shipping_id VARCHAR(36),
    failure_reason VARCHAR(500),
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_saga_state ON saga_instance(state);
CREATE INDEX IF NOT EXISTS idx_saga_updated ON saga_instance(updated_at);

-- Idempotency table (shared with common-messaging)
CREATE TABLE IF NOT EXISTS idempotency_event (
    event_id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
