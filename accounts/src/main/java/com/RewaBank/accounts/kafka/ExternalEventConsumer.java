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
            backoff = @Backoff(delay = 1000, multiplier = 2),
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

        pending.forEach(account -> {
            try {
                accountService.activateAccount(account.getId());
                log.info("Account auto-activated: {} for customer: {}",
                        account.getAccountNumber(), customerId);
            } catch (Exception e) {
                log.error("Failed to activate account {}: {}",
                        account.getId(), e.getMessage());
            }
        });
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 500),
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

        try {
            accountService.freezeAccount(UUID.fromString(accountId), reason);
            log.warn("Account auto-frozen due to fraud alert: {}", accountId);
        } catch (Exception e) {
            log.error("Failed to freeze account {}: {}", accountId, e.getMessage());
        }
    }
}
