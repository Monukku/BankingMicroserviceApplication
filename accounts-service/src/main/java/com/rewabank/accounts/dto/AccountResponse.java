package com.rewabank.accounts.dto;

import com.rewabank.accounts.entity.Account;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        String keycloakUserId,
        UUID customerId,
        Account.AccountType accountType,
        Account.AccountStatus status,
        BigDecimal balance,
        String currency,
        String branchCode,
        String ifscCode,
        LocalDateTime activatedAt,
        LocalDateTime createdAt,

        // FD / RD fields — null for SAVINGS/CURRENT/BUSINESS/SALARY
        BigDecimal depositAmount,
        Integer tenureMonths,
        BigDecimal interestRate,
        LocalDate maturityDate,
        UUID sourceAccountId,
        BigDecimal installmentAmount,      // RD only
        LocalDate nextInstallmentDate,     // RD only
        LocalDateTime maturedAt,

        // Salary fields — null for other types
        String employerName,
        String employerCode
) {}
