CREATE TABLE cards (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id      VARCHAR(255) NOT NULL,
    account_id            UUID         NOT NULL,
    card_number_encrypted TEXT         NOT NULL,
    card_last_four        VARCHAR(4)   NOT NULL,
    card_type             VARCHAR(10)  NOT NULL
                              CHECK (card_type IN ('DEBIT','CREDIT','PREPAID')),
    card_network          VARCHAR(12)  NOT NULL
                              CHECK (card_network IN ('VISA','MASTERCARD','RUPAY')),
    status                VARCHAR(12)  NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN (
                                  'PENDING','ACTIVE','BLOCKED',
                                  'CANCELLED','EXPIRED'
                              )),
    expiry_date           DATE         NOT NULL,
    cvv_hash              VARCHAR(255) NOT NULL,
    name_on_card          VARCHAR(100) NOT NULL,
    block_reason          TEXT,
    blocked_at            TIMESTAMP,
    blocked_by            VARCHAR(255),
    activated_at          TIMESTAMP,
    cancelled_at          TIMESTAMP,
    expiry_alert_sent     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);