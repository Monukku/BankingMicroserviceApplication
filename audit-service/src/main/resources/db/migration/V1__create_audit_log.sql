-- Append-only audit log
-- Partitioned by month for 7-year retention performance
CREATE TABLE audit_log (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    event_id        VARCHAR(255)  NOT NULL,
    event_type      VARCHAR(60)   NOT NULL,
    topic           VARCHAR(100)  NOT NULL,
    keycloak_user_id VARCHAR(255),
    aggregate_id    VARCHAR(255),
    aggregate_type  VARCHAR(60),
    payload         TEXT          NOT NULL,
    ip_address      VARCHAR(45),
    correlation_id  VARCHAR(100),
    occurred_at     TIMESTAMP,
    recorded_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

-- Create monthly partitions for current year + next year
-- In production: automate with pg_partman
CREATE TABLE audit_log_2026_01
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE audit_log_2026_02
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

CREATE TABLE audit_log_2026_03
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

CREATE TABLE audit_log_2026_04
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE TABLE audit_log_2026_05
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE audit_log_2026_06
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE audit_log_2026_07
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE TABLE audit_log_2026_08
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');

CREATE TABLE audit_log_2026_09
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');

CREATE TABLE audit_log_2026_10
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

CREATE TABLE audit_log_2026_11
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');

CREATE TABLE audit_log_2026_12
    PARTITION OF audit_log
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

-- Default partition catches anything outside range
CREATE TABLE audit_log_default
    PARTITION OF audit_log DEFAULT;

-- Prevent any UPDATE or DELETE on the entire table
CREATE RULE no_update_audit AS ON UPDATE TO audit_log DO INSTEAD NOTHING;
CREATE RULE no_delete_audit AS ON DELETE TO audit_log DO INSTEAD NOTHING;