package com.rewabank.loans.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO sent to Accounts service when crediting an account.
 *
 * Constructed in `LoanService.disburse(...)` as:
 * new AccountCreditRequest(accountId, amount, correlationId)
 */
public record AccountCreditRequest(
        UUID accountId,
        BigDecimal amount,
        String correlationId
) {}
