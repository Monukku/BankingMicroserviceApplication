package com.rewabank.loans.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class LoanDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verified_by")
    private String verifiedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}