CREATE TABLE accounts (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number       VARCHAR(12)   NOT NULL UNIQUE,
    keycloak_user_id     VARCHAR(255)  NOT NULL,
    customer_id          UUID          NOT NULL,
    account_type         VARCHAR(20)   NOT NULL
                             CHECK (account_type IN (
                                 'SAVINGS','CURRENT','FIXED_DEPOSIT',
                                 'RECURRING_DEPOSIT','SALARY'
                             )),
    status               VARCHAR(10)   NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN (
                                 'PENDING','ACTIVE','DORMANT','FROZEN','CLOSED'
                             )),
    balance              NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    minimum_balance      NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    interest_rate        NUMERIC(5,2)           DEFAULT 0.00,
    currency             VARCHAR(3)    NOT NULL DEFAULT 'INR',
    branch_code          VARCHAR(10),
    ifsc_code            VARCHAR(11),
    activated_at         TIMESTAMP,
    frozen_at            TIMESTAMP,
    frozen_reason        TEXT,
    closed_at            TIMESTAMP,
    last_transaction_at  TIMESTAMP,
    deleted_at           TIMESTAMP,
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP     NOT NULL DEFAULT NOW()
);