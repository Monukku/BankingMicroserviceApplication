package com.rewabank.loans.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.loan-applied:bank.loan.applied}")
    private String loanAppliedTopic;

    @Value("${kafka.topics.loan-approved:bank.loan.approved}")
    private String loanApprovedTopic;

    @Value("${kafka.topics.loan-rejected:bank.loan.rejected}")
    private String loanRejectedTopic;

    @Value("${kafka.topics.loan-disbursed:bank.loan.disbursed}")
    private String loanDisbursedTopic;

    public void publishLoanApplied(String loanId, String userId,
                                   String amount, String loanType) {
        publish(loanAppliedTopic, loanId, Map.of(
                "eventId",    UUID.randomUUID().toString(),
                "eventType",  "LOAN_APPLIED",
                "loanId",     loanId,
                "userId",     userId,
                "amount",     amount,
                "loanType",   loanType,
                "occurredAt", LocalDateTime.now().toString()
        ));
    }

    public void publishLoanApproved(String loanId, String userId,
                                    String amount, String accountId) {
        publish(loanApprovedTopic, loanId, Map.of(
                "eventId",   UUID.randomUUID().toString(),
                "eventType", "LOAN_APPROVED",
                "loanId",    loanId,
                "userId",    userId,
                "amount",    amount,
                "accountId", accountId,
                "occurredAt",LocalDateTime.now().toString()
        ));
    }

    public void publishLoanRejected(String loanId, String userId,
                                    String reason) {
        publish(loanRejectedTopic, loanId, Map.of(
                "eventId",   UUID.randomUUID().toString(),
                "eventType", "LOAN_REJECTED",
                "loanId",    loanId,
                "userId",    userId,
                "reason",    reason != null ? reason : "",
                "occurredAt",LocalDateTime.now().toString()
        ));
    }

    public void publishLoanDisbursed(String loanId, String userId,
                                     String amount, String accountId) {
        publish(loanDisbursedTopic, loanId, Map.of(
                "eventId",   UUID.randomUUID().toString(),
                "eventType", "LOAN_DISBURSED",
                "loanId",    loanId,
                "userId",    userId,
                "amount",    amount,
                "accountId", accountId,
                "occurredAt",LocalDateTime.now().toString()
        ));
    }

    private void publish(String topic, String key,
                         Map<String, Object> payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to {}: {}", topic,
                                ex.getMessage());
                    } else {
                        log.debug("Published to {} offset: {}", topic,
                                result.getRecordMetadata().offset());
                    }
                });
    }
}