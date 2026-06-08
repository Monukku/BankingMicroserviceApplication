-- ============================================================
-- V5__create_loan_documents.sql
-- RewaBank Loans MS — Loan Documents Table
-- ============================================================

CREATE TABLE loan_documents (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id        UUID         NOT NULL,
    document_type  VARCHAR(50)  NOT NULL,
    document_url   VARCHAR(500),
    verified_at    TIMESTAMP,
    verified_by    VARCHAR(255),
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_loan_docs_loan_id ON loan_documents(loan_id);
