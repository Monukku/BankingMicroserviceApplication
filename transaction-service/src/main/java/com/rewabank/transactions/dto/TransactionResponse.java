package com.rewabank.transactions.dto;

import com.rewabank.transactions.entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String idempotencyKey,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency,
        Transaction.TransactionType transactionType,
        Transaction.TransactionStatus status,
        String remarks,
        String referenceNumber,
        String fraudAction,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {}
