package com.rewabank.accounts.dto;

import java.math.BigDecimal;
import java.util.UUID;

// Received from Transactions MS for debit/credit operations
public record AccountDebitCreditRequest(
        UUID accountId,
        BigDecimal amount,
        String correlationId    // transaction ID for tracing
) {}