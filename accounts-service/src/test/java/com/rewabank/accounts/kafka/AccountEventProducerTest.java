package com.rewabank.accounts.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountEventProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks AccountEventProducer producer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "accountCreatedTopic",   "bank.account.created");
        ReflectionTestUtils.setField(producer, "accountActivatedTopic", "bank.account.activated");
        ReflectionTestUtils.setField(producer, "accountFrozenTopic",    "bank.account.frozen");
        ReflectionTestUtils.setField(producer, "accountClosedTopic",    "bank.account.closed");
        ReflectionTestUtils.setField(producer, "balanceUpdatedTopic",   "bank.balance.updated");
        // Return a never-completing future so whenComplete callback is not invoked during test
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<>());
    }

    // ── publishAccountCreated ──────────────────────────────────────────────────

    @Test
    void publishAccountCreated_sendsToKafka() {
        producer.publishAccountCreated("acct-1", "ACC001", "cust-1", "SAVINGS");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    // ── publishAccountActivated ────────────────────────────────────────────────

    @Test
    void publishAccountActivated_sendsToKafka() {
        producer.publishAccountActivated("acct-2", "ACC002", "cust-2");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    // ── publishAccountFrozen ───────────────────────────────────────────────────

    @Test
    void publishAccountFrozen_withReason_sendsToKafka() {
        producer.publishAccountFrozen("acct-3", "ACC003", "Fraud alert");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishAccountFrozen_nullReason_doesNotThrow() {
        // Kills NegateConditionals on: reason != null ? reason : ""
        producer.publishAccountFrozen("acct-3", "ACC003", null);
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    // ── publishAccountClosed ───────────────────────────────────────────────────

    @Test
    void publishAccountClosed_sendsToKafka() {
        producer.publishAccountClosed("acct-4", "ACC004", "cust-4");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    // ── publishBalanceUpdated ──────────────────────────────────────────────────

    @Test
    void publishBalanceUpdated_withCorrelationId_sendsToKafka() {
        producer.publishBalanceUpdated("acct-5", "ACC005", "DEBIT", "500.00", "9500.00", "corr-xyz");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishBalanceUpdated_nullCorrelationId_doesNotThrow() {
        // Kills NegateConditionals on: correlationId != null ? correlationId : ""
        producer.publishBalanceUpdated("acct-5", "ACC005", "CREDIT", "200.00", "10200.00", null);
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void allPublishMethods_eachCallKafkaSendOnce() {
        producer.publishAccountCreated("a", "ACC", "c", "SAVINGS");
        producer.publishAccountActivated("a", "ACC", "c");
        producer.publishAccountFrozen("a", "ACC", "reason");
        producer.publishAccountClosed("a", "ACC", "c");
        producer.publishBalanceUpdated("a", "ACC", "DEBIT", "100", "900", "corr");
        verify(kafkaTemplate, times(5)).send(anyString(), anyString(), any());
    }
}