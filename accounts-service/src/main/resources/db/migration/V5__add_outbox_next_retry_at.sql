ALTER TABLE outbox_events ADD COLUMN next_retry_at TIMESTAMP;

-- Replace the simple status index with one that includes next_retry_at
-- so the scheduler query (status=PENDING AND next_retry_at <= NOW()) is index-only
DROP INDEX IF EXISTS idx_outbox_status;
CREATE INDEX idx_outbox_pending_retry ON outbox_events(next_retry_at, created_at)
    WHERE status = 'PENDING';
