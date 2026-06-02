CREATE INDEX idx_loans_keycloak_id ON loan_applications(keycloak_user_id);
CREATE INDEX idx_loans_status      ON loan_applications(status, created_at DESC);
CREATE INDEX idx_loans_account_id  ON loan_applications(account_id);
CREATE INDEX idx_loans_outbox      ON loans_outbox_events(status, created_at)
    WHERE status = 'PENDING';