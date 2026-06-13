CREATE INDEX idx_banking_users_keycloak_id_active ON banking_users(keycloak_user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_banking_users_email_active       ON banking_users(email)            WHERE deleted_at IS NULL;
CREATE INDEX idx_banking_users_mobile_active      ON banking_users(mobile_number)    WHERE deleted_at IS NULL;
CREATE INDEX idx_otp_records_user_purpose  ON otp_records(user_id, purpose)   WHERE used = FALSE;
CREATE INDEX idx_otp_records_expires       ON otp_records(expires_at);
CREATE INDEX idx_token_blacklist_jti       ON token_blacklist(jti);
CREATE INDEX idx_token_blacklist_expires   ON token_blacklist(expires_at);