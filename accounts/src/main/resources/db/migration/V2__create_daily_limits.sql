CREATE TABLE daily_limits (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id          UUID          NOT NULL REFERENCES accounts(id),
    limit_date          DATE          NOT NULL,
    daily_debit_limit   NUMERIC(19,4) NOT NULL DEFAULT 100000.0000,
    used_debit_amount   NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    daily_credit_limit  NUMERIC(19,4) NOT NULL DEFAULT 500000.0000,
    used_credit_amount  NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE(account_id, limit_date)
);