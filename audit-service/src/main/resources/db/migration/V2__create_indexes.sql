-- Indexes per partition are created automatically in PostgreSQL 11+
CREATE INDEX idx_audit_event_id      ON audit_log(event_id);
CREATE INDEX idx_audit_keycloak_id   ON audit_log(keycloak_user_id, recorded_at DESC);
CREATE INDEX idx_audit_aggregate_id  ON audit_log(aggregate_id,     recorded_at DESC);
CREATE INDEX idx_audit_event_type    ON audit_log(event_type,        recorded_at DESC);
CREATE INDEX idx_audit_recorded_at   ON audit_log(recorded_at DESC);