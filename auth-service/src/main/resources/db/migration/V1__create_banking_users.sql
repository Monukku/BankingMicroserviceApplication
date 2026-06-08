-- ============================================================
-- V1__create_banking_users.sql
-- RewaBank Auth MS — Banking Users Table
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE banking_users (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id     VARCHAR(255)  NOT NULL UNIQUE,
    email                VARCHAR(255)  NOT NULL UNIQUE,
    mobile_number        VARCHAR(15)   NOT NULL UNIQUE,
    full_name            VARCHAR(255)  NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    kyc_verified         BOOLEAN       NOT NULL DEFAULT false,
    failed_otp_attempts  INTEGER       NOT NULL DEFAULT 0,
    otp_locked_until     TIMESTAMP,
    created_at           TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMP,

    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE INDEX idx_banking_users_keycloak_id ON banking_users(keycloak_user_id);
CREATE INDEX idx_banking_users_email       ON banking_users(email);
CREATE INDEX idx_banking_users_mobile      ON banking_users(mobile_number);
CREATE INDEX idx_banking_users_status      ON banking_users(status);
