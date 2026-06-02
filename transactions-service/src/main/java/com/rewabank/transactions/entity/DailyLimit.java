package com.rewabank.transactions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "txn_daily_limits")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Column(name = "limit_date", nullable = false)
    private LocalDate limitDate;

    // Per-day transfer limit — default 1L
    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal dailyLimit = new BigDecimal("100000.0000");

    // Amount used so far today
    @Column(name = "used_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal usedAmount = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public BigDecimal remainingLimit() {
        return dailyLimit.subtract(usedAmount);
    }

    public boolean hasCapacity(BigDecimal amount) {
        return usedAmount.add(amount).compareTo(dailyLimit) <= 0;
    }
}
