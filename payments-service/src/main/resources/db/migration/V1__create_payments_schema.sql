CREATE TABLE payment_orders (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key      VARCHAR(100) NOT NULL,
    keycloak_user_id     VARCHAR(255) NOT NULL,
    source_account_id    UUID         NOT NULL,
    source_account_number VARCHAR(12) NOT NULL,
    amount               DECIMAL(19,4) NOT NULL,
    currency             VARCHAR(3)   NOT NULL DEFAULT 'INR',
    payment_method       VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    razorpay_order_id    VARCHAR(100),
    razorpay_payment_id  VARCHAR(100),
    failure_reason       VARCHAR(500),
    completed_at         TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE outbox_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(255) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    topic        VARCHAR(255) NOT NULL,
    payload      JSONB        NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_orders_keycloak_user_id ON payment_orders(keycloak_user_id);
CREATE INDEX idx_payment_orders_razorpay_order_id ON payment_orders(razorpay_order_id);
CREATE INDEX idx_payment_orders_status ON payment_orders(status);
CREATE INDEX idx_outbox_events_unpublished ON outbox_events(published) WHERE published = FALSE;