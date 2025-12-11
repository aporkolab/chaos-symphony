-- Chaos Symphony - PostgreSQL Initialization
-- This script runs on first container start (when data volume is empty)

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Orders table with all valid statuses
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    customer_id VARCHAR(64),
    fraud_score NUMERIC(5,2),
    review_reason VARCHAR(500),
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    total NUMERIC(12,2) NOT NULL,
    shipping_address VARCHAR(500),
    CONSTRAINT orders_status_check CHECK (status IN (
        'NEW', 'PENDING_REVIEW', 'APPROVED', 'REJECTED',
        'PAID', 'ALLOCATED', 'SHIPPED', 'COMPLETED',
        'CANCELLED', 'PAYMENT_FAILED', 'INVENTORY_FAILED',
        'SHIPPING_FAILED', 'FAILED'
    ))
);

-- Outbox table for CDC
CREATE TABLE IF NOT EXISTS order_outbox (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Debezium publication
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'dbz_publication') THEN
        CREATE PUBLICATION dbz_publication FOR TABLE order_outbox;
    END IF;
END $$;

-- Idempotency table
CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Grant permissions (if debezium user exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'debezium') THEN
        GRANT SELECT ON order_outbox TO debezium;
    END IF;
END $$;
