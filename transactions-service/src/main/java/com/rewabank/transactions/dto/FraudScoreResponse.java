package com.rewabank.transactions.dto;

// Response from Fraud MS sync call
public record FraudScoreResponse(
        String accountId,
        int score,              // 0-100
        String action,          // APPROVE / FLAG / BLOCK
        String reason
) {}
