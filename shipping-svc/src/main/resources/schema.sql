-- This table is used by the JdbcIdempotencyStore to prevent duplicate message processing.
CREATE TABLE IF NOT EXISTS idempotency_event (
                                               event_id VARCHAR(255) PRIMARY KEY,
  seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  );
