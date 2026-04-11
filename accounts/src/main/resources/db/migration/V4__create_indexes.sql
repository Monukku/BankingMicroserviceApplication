CREATE INDEX idx_accounts_keycloak_id   ON accounts(keycloak_user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_accounts_customer_id   ON accounts(customer_id)      WHERE deleted_at IS NULL;
CREATE INDEX idx_accounts_number        ON accounts(account_number)    WHERE deleted_at IS NULL;
CREATE INDEX idx_accounts_status        ON accounts(status)            WHERE deleted_at IS NULL;
CREATE INDEX idx_accounts_last_txn      ON accounts(last_transaction_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_daily_limits_account   ON daily_limits(account_id, limit_date);
CREATE INDEX idx_outbox_status          ON outbox_events(status, created_at) WHERE status = 'PENDING';