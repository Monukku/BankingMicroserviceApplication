-- ============================================================
-- V6__create_rate_change_history.sql
-- RewaBank Loans MS — Rate Change History Table
-- ============================================================

CREATE TABLE rate_change_history (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id         UUID          NOT NULL,
    old_rate        NUMERIC(5,2)  NOT NULL,
    new_rate        NUMERIC(5,2)  NOT NULL,
    effective_date  DATE          NOT NULL,
    reason          VARCHAR(255),
    changed_by      VARCHAR(255),
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_rate_history_loan_id ON rate_change_history(loan_id);
