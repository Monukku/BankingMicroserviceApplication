-- ============================================================
-- V1__init_cards_schema.sql
-- RewaBank Cards MS — Initial Schema
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Cards Table ───────────────────────────────────────────────────────────────
CREATE TABLE cards (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id        VARCHAR(255)  NOT NULL,
    account_id              UUID          NOT NULL,
    customer_id             UUID          NOT NULL,
    card_number_encrypted   TEXT          NOT NULL,
    masked_card_number      VARCHAR(19)   NOT NULL,
    card_type               VARCHAR(20)   NOT NULL,
    delivery_type           VARCHAR(10)   NOT NULL,
    card_network            VARCHAR(15)   NOT NULL,
    status                  VARCHAR(30)   NOT NULL DEFAULT 'PENDING_ACTIVATION',

    expiry_month            INTEGER       NOT NULL,
    expiry_year             INTEGER       NOT NULL,
    cvv_hash                VARCHAR(255)  NOT NULL,
    name_on_card            VARCHAR(100)  NOT NULL,

    daily_limit             NUMERIC(15,2) NOT NULL DEFAULT 100000.00,
    monthly_limit           NUMERIC(15,2) NOT NULL DEFAULT 500000.00,
    international_enabled   BOOLEAN       NOT NULL DEFAULT false,
    contactless_enabled     BOOLEAN       NOT NULL DEFAULT true,
    online_enabled          BOOLEAN       NOT NULL DEFAULT true,

    -- Credit card fields
    credit_limit            NUMERIC(15,2),
    available_credit        NUMERIC(15,2),
    billing_cycle_day       INTEGER,
    statement_date          DATE,
    payment_due_date        DATE,
    minimum_due             NUMERIC(15,2),

    -- Physical card fields
    dispatch_address        TEXT,
    dispatched_at           TIMESTAMP,
    delivery_tracking_id    VARCHAR(100),
    delivered_at            TIMESTAMP,

    -- Lifecycle
    activated_at            TIMESTAMP,
    blocked_at              TIMESTAMP,
    block_reason            VARCHAR(255),
    blocked_by              VARCHAR(255),
    cancelled_at            TIMESTAMP,
    issued_by               VARCHAR(255),

    idempotency_key         VARCHAR(100) UNIQUE,
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_card_type      CHECK (card_type      IN ('DEBIT','CREDIT','PREPAID')),
    CONSTRAINT chk_delivery_type  CHECK (delivery_type  IN ('PHYSICAL','VIRTUAL')),
    CONSTRAINT chk_card_network   CHECK (card_network   IN ('VISA','MASTERCARD','RUPAY')),
    CONSTRAINT chk_card_status    CHECK (status         IN (
        'PENDING_ACTIVATION','ACTIVE','BLOCKED','CANCELLED','EXPIRED'
    )),
    CONSTRAINT chk_billing_day    CHECK (billing_cycle_day BETWEEN 1 AND 28)
);

-- ── Card Transactions Table ───────────────────────────────────────────────────
CREATE TABLE card_transactions (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id             UUID          NOT NULL REFERENCES cards(id),
    account_id          UUID          NOT NULL,
    amount              NUMERIC(15,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'INR',
    merchant_name       VARCHAR(255),
    merchant_category   VARCHAR(100),
    transaction_type    VARCHAR(30)   NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    channel             VARCHAR(20)   NOT NULL,
    is_international    BOOLEAN       NOT NULL DEFAULT false,
    fraud_score         INTEGER,
    declined_reason     VARCHAR(255),
    reference_id        VARCHAR(100),
    idempotency_key     VARCHAR(100)  UNIQUE,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_txn_type   CHECK (transaction_type IN (
        'PURCHASE','CASH_WITHDRAWAL','REFUND','EMI_CONVERSION',
        'CREDIT_PAYMENT','REVERSAL','BALANCE_INQUIRY'
    )),
    CONSTRAINT chk_txn_status CHECK (status IN ('PENDING','APPROVED','DECLINED','REVERSED','SETTLED')),
    CONSTRAINT chk_channel    CHECK (channel IN ('ONLINE','POS','ATM','CONTACTLESS','INTERNAL'))
);

-- ── Outbox Events Table ───────────────────────────────────────────────────────
CREATE TABLE outbox_events (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    topic         VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count   INTEGER      DEFAULT 0,
    error_message VARCHAR(500),
    published_at  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PUBLISHED','FAILED'))
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
CREATE INDEX idx_cards_keycloak_id    ON cards(keycloak_user_id);
CREATE INDEX idx_cards_account_id     ON cards(account_id);
CREATE INDEX idx_cards_customer_id    ON cards(customer_id);
CREATE INDEX idx_cards_status         ON cards(status);
CREATE INDEX idx_cards_card_number    ON cards(masked_card_number);

CREATE INDEX idx_card_txn_card_id     ON card_transactions(card_id);
CREATE INDEX idx_card_txn_account_id  ON card_transactions(account_id);
CREATE INDEX idx_card_txn_status      ON card_transactions(status);
CREATE INDEX idx_card_txn_created_at  ON card_transactions(created_at);

CREATE INDEX idx_outbox_status        ON outbox_events(status);
CREATE INDEX idx_outbox_created_at    ON outbox_events(created_at);
