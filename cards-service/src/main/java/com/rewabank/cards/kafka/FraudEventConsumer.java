package com.rewabank.cards.kafka;

import com.rewabank.cards.service.CardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Auto-blocks cards when fraud alert raised.
 * No OTP required for auto-block — fraud system overrides RBI mandate.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudEventConsumer {

    private final CardService cardService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 500),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = "bank.fraud.alert.raised",
            groupId = "cards-ms-fraud"
    )
    public void handleFraudAlert(Map<String, Object> event) {
        String accountId = (String) event.get("accountId");
        String action    = (String) event.getOrDefault("action", "FLAG");
        String reason    = (String) event.getOrDefault(
                "reason", "Fraud alert detected");

        if (accountId == null) return;

        // Auto-block only on BLOCK action — not FLAG
        if ("BLOCK".equals(action)) {
            log.warn("Auto-blocking cards for account: {} reason: {}",
                    accountId, reason);
            try {
                cardService.autoBlockByAccountId(
                        UUID.fromString(accountId), reason);
            } catch (Exception e) {
                log.error("Failed to auto-block cards for account: {} error: {}",
                        accountId, e.getMessage());
                throw e;
            }
        }
    }
}