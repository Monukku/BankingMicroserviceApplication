package com.rewabank.accounts.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "daily_limits")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "limit_date", nullable = false)
    private LocalDate limitDate;

    @Column(name = "daily_debit_limit", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal dailyDebitLimit = new BigDecimal("100000.00"); // 1L default

    @Column(name = "used_debit_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal usedDebitAmount = BigDecimal.ZERO;

    @Column(name = "daily_credit_limit", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal dailyCreditLimit = new BigDecimal("500000.00"); // 5L default

    @Column(name = "used_credit_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal usedCreditAmount = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean hasDebitCapacity(BigDecimal amount) {
        return usedDebitAmount.add(amount)
                .compareTo(dailyDebitLimit) <= 0;
    }
}
