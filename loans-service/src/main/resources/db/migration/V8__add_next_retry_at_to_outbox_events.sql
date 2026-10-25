ALTER TABLE loans_outbox_events
    ADD COLUMN next_retry_at TIMESTAMP;

CREATE INDEX idx_loans_outbox_pending
    ON loans_outbox_events (status, retry_count, next_retry_at)
    WHERE status = 'PENDING';