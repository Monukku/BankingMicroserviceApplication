-- ============================================================
-- V1__init_cards_schema.sql
-- RewaBank Cards MS — Initial Schema (aligned with entities)
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Cards Table ───────────────────────────────────────────────────────────────
CREATE TABLE cards (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id        VARCHAR(255)  NOT NULL,
    account_id              UUID          NOT NULL,
    customer_id             UUID,
    card_number_encrypted   TEXT          NOT NULL,
    card_last_four          VARCHAR(4)    NOT NULL,
    card_type               VARCHAR(20)   NOT NULL,
    card_network            VARCHAR(15)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    expiry_date             DATE          NOT NULL,
    cvv_hash                VARCHAR(255)  NOT NULL,
    name_on_card            VARCHAR(255)  NOT NULL,
    block_reason            VARCHAR(255),
    blocked_at              TIMESTAMP,
    blocked_by              VARCHAR(255),
    activated_at            TIMESTAMP,
    cancelled_at            TIMESTAMP,
    expiry_alert_sent       BOOLEAN       NOT NULL DEFAULT false,
    daily_limit             NUMERIC(15,2) NOT NULL DEFAULT 100000.00,
    monthly_limit           NUMERIC(15,2) NOT NULL DEFAULT 500000.00,
    international_enabled   BOOLEAN       NOT NULL DEFAULT true,
    online_enabled          BOOLEAN       NOT NULL DEFAULT true,
    idempotency_key         VARCHAR(100)  UNIQUE,
    created_at              TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_card_type    CHECK (card_type    IN ('DEBIT','CREDIT','PREPAID')),
    CONSTRAINT chk_card_network CHECK (card_network IN ('VISA','MASTERCARD','RUPAY')),
    CONSTRAINT chk_card_status  CHECK (status       IN ('PENDING','ACTIVE','BLOCKED','CANCELLED','EXPIRED'))
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
    channel             VARCHAR(20),
    is_international    BOOLEAN       NOT NULL DEFAULT false,
    fraud_score         INTEGER,
    declined_reason     VARCHAR(255),
    idempotency_key     VARCHAR(100)  UNIQUE,
    authorized_at       TIMESTAMP,
    settled_at          TIMESTAMP,
    declined_at         TIMESTAMP,
    reversed_at         TIMESTAMP,
    reverse_reason      VARCHAR(255),
    description         VARCHAR(500),
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_txn_type   CHECK (transaction_type IN (
        'PURCHASE','CASH_WITHDRAWAL','REFUND','EMI_CONVERSION',
        'CREDIT_PAYMENT','REVERSAL','ONLINE'
    )),
    CONSTRAINT chk_txn_status CHECK (status IN ('PENDING','AUTHORIZED','SETTLED','DECLINED','REVERSED')),
    CONSTRAINT chk_channel    CHECK (channel IN ('ONLINE','POS','ATM','CONTACTLESS'))
);

-- ── Outbox Events Table ───────────────────────────────────────────────────────
CREATE TABLE outbox_events (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  VARCHAR(255) NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    published     BOOLEAN      NOT NULL DEFAULT false,
    published_at  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
CREATE INDEX idx_cards_keycloak_id  ON cards(keycloak_user_id);
CREATE INDEX idx_cards_account_id   ON cards(account_id);
CREATE INDEX idx_cards_status       ON cards(status);
CREATE INDEX idx_cards_expiry       ON cards(expiry_date);

CREATE INDEX idx_card_txn_card_id   ON card_transactions(card_id);
CREATE INDEX idx_card_txn_status    ON card_transactions(status);
CREATE INDEX idx_card_txn_created   ON card_transactions(created_at);

CREATE INDEX idx_outbox_published   ON outbox_events(published);
CREATE INDEX idx_outbox_created_at  ON outbox_events(created_at);
