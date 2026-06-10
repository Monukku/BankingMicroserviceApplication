package com.rewabank.loans.dto;

import com.rewabank.loans.entity.LoanApplication;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LoanApplicationResponse(
        UUID id,
        String keycloakUserId,
        UUID accountId,
        LoanApplication.LoanType loanType,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        BigDecimal interestRate,
        Integer tenureMonths,
        LoanApplication.LoanStatus status,
        String purpose,
        String rejectionReason,
        String reviewNotes,
        Integer fraudScore,
        String fraudAction,
        LocalDateTime appliedAt,
        LocalDateTime approvedAt,
        LocalDateTime disbursedAt,
        LocalDateTime createdAt
) {}