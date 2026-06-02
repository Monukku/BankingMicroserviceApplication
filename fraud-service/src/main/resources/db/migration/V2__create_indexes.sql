CREATE INDEX idx_fraud_account_id ON fraud_alerts(account_id);
CREATE INDEX idx_fraud_status     ON fraud_alerts(status, created_at DESC);
CREATE INDEX idx_fraud_txn_id     ON fraud_alerts(transaction_id);