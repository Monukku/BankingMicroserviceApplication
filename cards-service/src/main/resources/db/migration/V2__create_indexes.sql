CREATE INDEX idx_cards_keycloak_id ON cards(keycloak_user_id);
CREATE INDEX idx_cards_account_id  ON cards(account_id);
CREATE INDEX idx_cards_status      ON cards(status);
CREATE INDEX idx_cards_expiry      ON cards(expiry_date) WHERE status = 'ACTIVE';