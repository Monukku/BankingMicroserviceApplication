CREATE INDEX idx_customers_keycloak_id ON customers(keycloak_user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_customers_email       ON customers(email)            WHERE deleted_at IS NULL;
CREATE INDEX idx_customers_kyc_status  ON customers(kyc_status)       WHERE deleted_at IS NULL;
CREATE INDEX idx_addresses_customer    ON addresses(customer_id);
CREATE INDEX idx_kyc_docs_customer     ON kyc_documents(customer_id);
CREATE INDEX idx_kyc_docs_type         ON kyc_documents(customer_id, document_type);