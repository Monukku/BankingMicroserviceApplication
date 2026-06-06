ALTER TABLE cards ADD COLUMN idempotency_key VARCHAR(255);
CREATE UNIQUE INDEX idx_cards_idempotency ON cards(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
