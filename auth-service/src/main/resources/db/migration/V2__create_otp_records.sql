-- ============================================================
-- V2__create_otp_records.sql
-- RewaBank Auth MS — OTP Records Table
-- ============================================================

CREATE TABLE otp_records (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL REFERENCES banking_users(id),
    otp_hash      VARCHAR(255)  NOT NULL,
    purpose       VARCHAR(30)   NOT NULL,
    reference_id  VARCHAR(255),
    expires_at    TIMESTAMP     NOT NULL,
    used          BOOLEAN       NOT NULL DEFAULT false,
    ip_address    VARCHAR(45),
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_otp_purpose CHECK (purpose IN (
        'TRANSFER', 'CARD_BLOCK', 'CARD_UNBLOCK',
        'BENEFICIARY_ADD', 'PASSWORD_RESET', 'DEVICE_REGISTER'
    ))
);

CREATE INDEX idx_otp_records_user_id    ON otp_records(user_id);
CREATE INDEX idx_otp_records_purpose    ON otp_records(purpose);
CREATE INDEX idx_otp_records_used       ON otp_records(used);
CREATE INDEX idx_otp_records_expires_at ON otp_records(expires_at);
