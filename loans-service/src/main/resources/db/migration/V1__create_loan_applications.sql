CREATE TABLE loan_applications (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id  VARCHAR(255)  NOT NULL,
    account_id        UUID          NOT NULL,
    loan_type         VARCHAR(20)   NOT NULL
                          CHECK (loan_type IN (
                              'PERSONAL','HOME','VEHICLE',
                              'EDUCATION','BUSINESS','GOLD'
                          )),
    requested_amount  NUMERIC(19,4) NOT NULL,
    approved_amount   NUMERIC(19,4),
    interest_rate     NUMERIC(5,2),
    tenure_months     INTEGER       NOT NULL,
    status            VARCHAR(15)   NOT NULL DEFAULT 'APPLIED'
                          CHECK (status IN (
                              'APPLIED','UNDER_REVIEW','APPROVED',
                              'DISBURSED','REJECTED','CLOSED'
                          )),
    purpose           VARCHAR(500),
    reviewer_id       VARCHAR(255),
    reviewer_role     VARCHAR(30),
    rejection_reason  TEXT,
    review_notes      VARCHAR(500),
    applied_at        TIMESTAMP,
    reviewed_at       TIMESTAMP,
    approved_at       TIMESTAMP,
    disbursed_at      TIMESTAMP,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW()
);