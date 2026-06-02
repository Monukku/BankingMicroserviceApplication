CREATE TABLE otp_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID          NOT NULL REFERENCES banking_users(id),
    otp_hash        VARCHAR(255)  NOT NULL,
    purpose         VARCHAR(30)   NOT NULL
                        CHECK (purpose IN (
                            'TRANSFER','CARD_BLOCK','CARD_UNBLOCK',
                            'BENEFICIARY_ADD','PASSWORD_RESET','DEVICE_REGISTER'
                        )),
    reference_id    VARCHAR(255),
    expires_at      TIMESTAMP     NOT NULL,
    used            BOOLEAN       NOT NULL DEFAULT FALSE,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);