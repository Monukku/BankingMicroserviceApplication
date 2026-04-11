CREATE TABLE beneficiaries (
    id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id            VARCHAR(255) NOT NULL,
    beneficiary_account_number  VARCHAR(12)  NOT NULL,
    beneficiary_name            VARCHAR(100) NOT NULL,
    beneficiary_bank            VARCHAR(100),
    ifsc_code                   VARCHAR(11),
    is_active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    cooling_period_ends_at      TIMESTAMP    NOT NULL,
    deleted_at                  TIMESTAMP,
    created_at                  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(keycloak_user_id, beneficiary_account_number)
);