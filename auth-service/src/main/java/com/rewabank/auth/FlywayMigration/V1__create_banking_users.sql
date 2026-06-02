CREATE TABLE banking_users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id    VARCHAR(255)  NOT NULL UNIQUE,
    email               VARCHAR(255)  NOT NULL UNIQUE,
    mobile_number       VARCHAR(15)   NOT NULL UNIQUE,
    full_name           VARCHAR(100)  NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE','SUSPENDED','DELETED')),
    kyc_verified        BOOLEAN       NOT NULL DEFAULT FALSE,
    failed_otp_attempts INTEGER       NOT NULL DEFAULT 0,
    otp_locked_until    TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP
);