CREATE TABLE customers (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id        VARCHAR(255)  NOT NULL UNIQUE,
    email                   VARCHAR(255)  NOT NULL,
    full_name               VARCHAR(100)  NOT NULL,
    mobile_number           VARCHAR(15)   NOT NULL,
    date_of_birth           DATE,
    aadhaar_number_encrypted TEXT,          -- AES-256-GCM encrypted
    pan_number_encrypted    TEXT,          -- AES-256-GCM encrypted
    kyc_status              VARCHAR(20)   NOT NULL DEFAULT 'NOT_SUBMITTED'
                                CHECK (kyc_status IN (
                                    'NOT_SUBMITTED','SUBMITTED',
                                    'UNDER_REVIEW','VERIFIED','REJECTED'
                                )),
    kyc_submitted_at        TIMESTAMP,
    kyc_verified_at         TIMESTAMP,
    kyc_verified_by         VARCHAR(255),
    kyc_rejection_reason    TEXT,
    deleted_at              TIMESTAMP,
    created_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP     NOT NULL DEFAULT NOW()
);