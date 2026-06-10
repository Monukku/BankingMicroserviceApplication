ALTER TABLE loan_applications
    ADD COLUMN fraud_score  INTEGER,
    ADD COLUMN fraud_action VARCHAR(10);