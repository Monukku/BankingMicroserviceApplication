-- ============================================================
-- V4__create_emi_schedules.sql
-- RewaBank Loans MS — EMI Schedules Table
-- ============================================================

CREATE TABLE emi_schedules (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id             UUID          NOT NULL,
    installment_number  INTEGER       NOT NULL,
    due_date            DATE          NOT NULL,
    emi_amount          NUMERIC(15,2) NOT NULL,
    principal_part      NUMERIC(15,2) NOT NULL,
    interest_part       NUMERIC(15,2) NOT NULL,
    outstanding_after   NUMERIC(15,2) NOT NULL,
    penalty_amount      NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    paid_amount         NUMERIC(15,2),
    paid_at             TIMESTAMP,
    transaction_id      UUID,
    idempotency_key     VARCHAR(100)  UNIQUE,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_emi_status CHECK (status IN (
        'PENDING','PAID','OVERDUE','PARTIALLY_PAID','WAIVED'
    ))
);

CREATE INDEX idx_emi_loan_id  ON emi_schedules(loan_id);
CREATE INDEX idx_emi_due_date ON emi_schedules(due_date);
CREATE INDEX idx_emi_status   ON emi_schedules(status);
