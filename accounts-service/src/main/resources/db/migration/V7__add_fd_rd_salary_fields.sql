-- FD / RD / Salary extra columns on accounts table
-- Flyway runs this automatically on next startup

-- Allow BUSINESS in the existing CHECK (was missing from V1)
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_account_type_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_account_type_check
    CHECK (account_type IN (
        'SAVINGS','CURRENT','FIXED_DEPOSIT',
        'RECURRING_DEPOSIT','SALARY','BUSINESS'
    ));

-- Fixed / Recurring Deposit fields
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS deposit_amount        NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS tenure_months         INTEGER,
    ADD COLUMN IF NOT EXISTS maturity_date         DATE,
    ADD COLUMN IF NOT EXISTS source_account_id     UUID,          -- savings/current from which FD funded
    ADD COLUMN IF NOT EXISTS installment_amount    NUMERIC(19,4), -- RD monthly amount
    ADD COLUMN IF NOT EXISTS next_installment_date DATE,          -- RD next debit date
    ADD COLUMN IF NOT EXISTS matured_at            TIMESTAMP;     -- set when FD matures

-- Salary account fields
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS employer_name  VARCHAR(200),
    ADD COLUMN IF NOT EXISTS employer_code  VARCHAR(50);

-- Index for RD scheduler — find due installments quickly
CREATE INDEX IF NOT EXISTS idx_accounts_rd_next_installment
    ON accounts (next_installment_date)
    WHERE account_type = 'RECURRING_DEPOSIT'
      AND status = 'ACTIVE'
      AND deleted_at IS NULL;

-- Index for FD maturity scheduler
CREATE INDEX IF NOT EXISTS idx_accounts_fd_maturity
    ON accounts (maturity_date)
    WHERE account_type = 'FIXED_DEPOSIT'
      AND status = 'ACTIVE'
      AND deleted_at IS NULL;
