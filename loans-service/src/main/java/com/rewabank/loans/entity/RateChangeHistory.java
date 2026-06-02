package com.rewabank.loans.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rate_change_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RateChangeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "old_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal oldRate;

    @Column(name = "new_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal newRate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "changed_by")
    private String changedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}