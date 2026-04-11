
CREATE INDEX idx_txn_keycloak_id   ON transactions(keycloak_user_id);
CREATE INDEX idx_txn_source_acct   ON transactions(source_account_id);
CREATE INDEX idx_txn_dest_acct     ON transactions(destination_account_id);
CREATE INDEX idx_txn_status        ON transactions(status);
CREATE INDEX idx_txn_idempotency   ON transactions(idempotency_key);
CREATE INDEX idx_ledger_txn_id     ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_account_id ON ledger_entries(account_id);
CREATE INDEX idx_beneficiary_user  ON beneficiaries(keycloak_user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_daily_limit_user  ON txn_daily_limits(keycloak_user_id, limit_date);
CREATE INDEX idx_outbox_status     ON txn_outbox_events(status, created_at) WHERE status = 'PENDING';