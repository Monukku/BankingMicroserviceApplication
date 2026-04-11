package com.rewabank.accounts.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// CQRS read side — served from Redis
public record BalanceResponse(
        String accountNumber,
        BigDecimal balance,
        BigDecimal availableBalance,
        String currency,
        String status,
        LocalDateTime asOf          // when this balance was last synced
) {}
