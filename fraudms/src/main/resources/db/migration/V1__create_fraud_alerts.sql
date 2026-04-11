CREATE TABLE fraud_alerts (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID          NOT NULL,
    keycloak_user_id VARCHAR(255),
    transaction_id  VARCHAR(255),
    amount          NUMERIC(19,4) NOT NULL,
    fraud_score     INTEGER       NOT NULL,
    action          VARCHAR(10)   NOT NULL
                        CHECK (action IN ('FLAG','BLOCK')),
    triggered_rules TEXT,
    status          VARCHAR(20)   NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN','RESOLVED','FALSE_POSITIVE')),
    resolved_by     VARCHAR(255),
    resolved_at     TIMESTAMP,
    resolution_notes TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);