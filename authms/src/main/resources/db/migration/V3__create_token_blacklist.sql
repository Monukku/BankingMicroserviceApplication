-- Token blacklist for logout (TTL managed by cleanup job)
CREATE TABLE token_blacklist (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jti         VARCHAR(255) NOT NULL UNIQUE,  -- JWT ID claim
    user_id     UUID         NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);