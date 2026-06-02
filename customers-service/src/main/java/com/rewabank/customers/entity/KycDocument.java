package com.rewabank.customers.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    // MinIO object path — never expose full URL to client
    @Column(name = "minio_object_key", nullable = false)
    private String minioObjectKey;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum DocumentType {
        AADHAAR_FRONT,
        AADHAAR_BACK,
        PAN_CARD,
        PASSPORT,
        VOTER_ID,
        DRIVING_LICENSE,
        SELFIE,
        SIGNATURE
    }

    public enum DocumentStatus {
        UPLOADED,
        UNDER_REVIEW,
        VERIFIED,
        REJECTED
    }
}
