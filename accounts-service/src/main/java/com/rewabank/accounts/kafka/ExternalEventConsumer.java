package com.rewabank.accounts.kafka;

import com.rewabank.accounts.entity.Account;
import com.rewabank.accounts.services.AccountService;
import com.rewabank.accounts.repository.AccountsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalEventConsumer {

    private final AccountService    accountService;
    private final AccountsRepository accountRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2, random = true),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = "${kafka.topics.kyc-verified:bank.kyc.verified}",
            groupId = "${kafka.consumer.group-id:accounts-ms}"
    )
    public void handleKycVerified(Map<String, Object> event) {
        String customerId = (String) event.get("customerId");
        log.info("KYC_VERIFIED received for customerId: {}", customerId);

        List<Account> pending = accountRepository
                .findPendingByCustomerId(UUID.fromString(customerId));

        // Process all accounts before deciding whether to retry. Collecting
        // failures and rethrowing after the loop means @RetryableTopic retries
        // the whole message while accounts-already-activated are idempotent no-ops.
        // Swallowing exceptions here committed the Kafka offset, losing the failure.
        List<String> failures = new ArrayList<>();
        pending.forEach(account -> {
            try {
                accountService.activateAccount(account.getId());
                log.info("Account auto-activated: {} for customer: {}",
                        account.getAccountNumber(), customerId);
            } catch (Exception e) {
                log.error("Failed to activate account {}: {}",
                        account.getId(), e.getMessage());
                failures.add(account.getId().toString());
            }
        });

        if (!failures.isEmpty()) {
            throw new RuntimeException(
                    "Failed to activate accounts " + failures + " for customer: " + customerId);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2, random = true),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = "${kafka.topics.payment-completed:bank.payment.completed}",
            groupId = "${kafka.consumer.group-id:accounts-ms}"
    )
    public void handlePaymentCompleted(Map<String, Object> event) {
        String paymentOrderId  = (String) event.get("paymentOrderId");
        String sourceAccountId = (String) event.get("sourceAccountId");
        String amountStr       = (String) event.get("amount");

        log.info("PAYMENT_COMPLETED received — paymentOrderId: {} sourceAccountId: {}",
                paymentOrderId, sourceAccountId);

        try {
            java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);
            accountService.credit(
                    java.util.UUID.fromString(sourceAccountId),
                    amount,
                    paymentOrderId
            );
            log.info("Account credited ₹{} for payment: {}", amount, paymentOrderId);
        } catch (Exception e) {
            log.error("Failed to credit account for payment {}: {}", paymentOrderId, e.getMessage());
            throw e;
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 500, random = true),
            dltTopicSuffix = ".dlq",
            retryTopicSuffix = ".retry"
    )
    @KafkaListener(
            topics = "${kafka.topics.fraud-alert:bank.fraud.alert.raised}",
            groupId = "${kafka.consumer.group-id:accounts-ms}"
    )
    public void handleFraudAlert(Map<String, Object> event) {
        String accountId = (String) event.get("accountId");
        String reason    = (String) event.getOrDefault("reason",
                "Automatic freeze due to fraud alert");

        log.warn("FRAUD_ALERT received for accountId: {}", accountId);
        // No try-catch — let exceptions propagate to @RetryableTopic.
        // Swallowing here committed the Kafka offset, silently dropping failed freezes.
        accountService.freezeAccount(UUID.fromString(accountId), reason);
        log.warn("Account auto-frozen due to fraud alert: {}", accountId);
    }
}
