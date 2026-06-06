package com.rewabank.accounts.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

// Received from Transactions MS for debit/credit operations
public record AccountDebitCreditRequest(
        UUID accountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Correlation ID is required")
        String correlationId    // transaction ID for tracing
) {}