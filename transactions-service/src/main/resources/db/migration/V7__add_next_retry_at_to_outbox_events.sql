ALTER TABLE txn_outbox_events
    ADD COLUMN next_retry_at TIMESTAMP;

CREATE INDEX idx_txn_outbox_pending
    ON txn_outbox_events (status, retry_count, next_retry_at)
    WHERE status = 'PENDING';