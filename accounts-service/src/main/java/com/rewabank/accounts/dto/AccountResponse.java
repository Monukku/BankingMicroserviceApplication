package com.rewabank.accounts.dto;

import com.rewabank.accounts.entity.Account;
import java.math.BigDecimal;
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
        LocalDateTime createdAt
) {}
