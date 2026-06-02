package com.rewabank.transactions.dto;

import java.math.BigDecimal;
import java.util.UUID;

// Sent to Accounts MS for debit/credit operations
public record AccountDebitCreditRequest(
        UUID accountId,
        BigDecimal amount,
        String correlationId    // transaction ID for tracing
) {}
