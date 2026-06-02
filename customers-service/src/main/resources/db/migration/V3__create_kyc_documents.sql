CREATE TABLE kyc_documents (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID          NOT NULL REFERENCES customers(id),
    document_type       VARCHAR(30)   NOT NULL
                            CHECK (document_type IN (
                                'AADHAAR_FRONT','AADHAAR_BACK','PAN_CARD',
                                'PASSPORT','VOTER_ID','DRIVING_LICENSE',
                                'SELFIE','SIGNATURE'
                            )),
    minio_object_key    TEXT          NOT NULL,
    original_file_name  VARCHAR(255)  NOT NULL,
    content_type        VARCHAR(100)  NOT NULL,
    file_size_bytes     BIGINT        NOT NULL,
    status              VARCHAR(15)   NOT NULL DEFAULT 'UPLOADED'
                            CHECK (status IN ('UPLOADED','UNDER_REVIEW','VERIFIED','REJECTED')),
    rejection_reason    TEXT,
    verified_by         VARCHAR(255),
    verified_at         TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);