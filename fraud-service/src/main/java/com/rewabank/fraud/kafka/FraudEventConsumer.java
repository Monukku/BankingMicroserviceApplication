package com.rewabank.fraud.kafka;

import com.rewabank.fraud.service.FraudAlertService;
import com.rewabank.fraud.service.FraudScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Async pattern analysis — retrospective fraud detection.
 * Complements the sync inline score check.
 * Detects patterns across multiple transactions over time.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudEventConsumer {

    private final FraudScoringService fraudScoringService;
    private final FraudAlertService   fraudAlertService;

    // Consume completed transactions for pattern analysis
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = "bank.transaction.completed",
            groupId = "fraud-ms-transactions"
    )
    public void handleTransactionCompleted(Map<String, Object> event) {
        String accountId     = (String) event.get("sourceAccountId");
        String amount        = (String) event.getOrDefault("amount", "0");
        String transactionId = (String) event.get("transactionId");

        if (accountId == null) return;

        // Re-score for pattern analysis (velocity window already updated)
        FraudScoringService.ScoringResult result = fraudScoringService.score(
                UUID.fromString(accountId),
                new BigDecimal(amount),
                "TRANSFER",
                transactionId
        );

        // If retrospective analysis finds high risk — raise alert
        if ("BLOCK".equals(result.action()) || "FLAG".equals(result.action())) {
            if (result.score() >= 80) {
                fraudAlertService.createAlert(
                        UUID.fromString(accountId),
                        (String) event.get("keycloakUserId"),
                        transactionId,
                        new BigDecimal(amount),
                        result.score(),
                        result.action(),
                        result.triggeredRules()
                );
            }
        }
    }

    // Consume balance updates — detect large sudden drops
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = "bank.balance.updated",
            groupId = "fraud-ms-balance"
    )
    public void handleBalanceUpdated(Map<String, Object> event) {
        String direction      = (String) event.getOrDefault("direction", "");
        String accountId      = (String) event.get("accountId");
        String previousBalance = (String) event.getOrDefault("previousBalance", "0");
        String newBalance      = (String) event.getOrDefault("newBalance", "0");

        if (!"DEBIT".equals(direction) || accountId == null) return;

        try {
            BigDecimal prev = new BigDecimal(previousBalance);
            BigDecimal curr = new BigDecimal(newBalance);

            // Alert if balance drops by more than 80% in one transaction
            if (prev.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dropPercent = prev.subtract(curr)
                        .divide(prev, 2, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

                if (dropPercent.compareTo(new BigDecimal("80")) >= 0) {
                    log.warn("Large balance drop detected: {}% for account: {}",
                            dropPercent, accountId);
                    fraudAlertService.createAlert(
                            UUID.fromString(accountId),
                            (String) event.get("keycloakUserId"),
                            null,
                            prev.subtract(curr),
                            85,
                            "FLAG",
                            "LARGE_BALANCE_DROP(+85)"
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to analyse balance event: {}", e.getMessage());
        }
    }

    // Mark new accounts in fraud engine when created
    @KafkaListener(
            topics = "bank.account.created",
            groupId = "fraud-ms-accounts"
    )
    public void handleAccountCreated(Map<String, Object> event) {
        String accountId = (String) event.get("accountId");
        if (accountId != null) {
            fraudScoringService.markNewAccount(UUID.fromString(accountId));
        }
    }
}
