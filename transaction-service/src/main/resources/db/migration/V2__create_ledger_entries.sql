CREATE TABLE ledger_entries (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID          NOT NULL REFERENCES transactions(id),
    account_id      UUID          NOT NULL,
    account_number  VARCHAR(12)   NOT NULL,
    entry_type      VARCHAR(6)    NOT NULL CHECK (entry_type IN ('DEBIT','CREDIT')),
    amount          NUMERIC(19,4) NOT NULL,
    balance_after   NUMERIC(19,4) NOT NULL,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'INR',
    description     VARCHAR(500),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);