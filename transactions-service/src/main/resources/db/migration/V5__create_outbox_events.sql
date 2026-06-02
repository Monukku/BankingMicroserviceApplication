CREATE TABLE txn_outbox_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(255) NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL DEFAULT 'Transaction',
    event_type      VARCHAR(50)  NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT,
    processed_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);