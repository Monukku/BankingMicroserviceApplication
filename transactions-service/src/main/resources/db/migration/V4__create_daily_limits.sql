CREATE TABLE txn_daily_limits (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id VARCHAR(255)  NOT NULL,
    limit_date       DATE          NOT NULL,
    daily_limit      NUMERIC(19,4) NOT NULL DEFAULT 100000.0000,
    used_amount      NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE(keycloak_user_id, limit_date)
);