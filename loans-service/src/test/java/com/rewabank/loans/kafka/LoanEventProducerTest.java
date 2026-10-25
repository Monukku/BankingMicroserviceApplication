package com.rewabank.loans.kafka;

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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanEventProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks LoanEventProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReflectionTestUtils.setField(producer, "loanAppliedTopic",   "bank.loan.applied");
        ReflectionTestUtils.setField(producer, "loanApprovedTopic",  "bank.loan.approved");
        ReflectionTestUtils.setField(producer, "loanRejectedTopic",  "bank.loan.rejected");
        ReflectionTestUtils.setField(producer, "loanDisbursedTopic", "bank.loan.disbursed");
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<>());
    }

    @Test
    void publishLoanApplied_sendsToKafka() {
        producer.publishLoanApplied("loan-1", "user-1", "100000.00", "PERSONAL");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishLoanApproved_sendsToKafka() {
        producer.publishLoanApproved("loan-2", "user-2", "200000.00", "acct-1");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishLoanRejected_withReason_sendsToKafka() {
        producer.publishLoanRejected("loan-3", "user-3", "Low credit score");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishLoanRejected_nullReason_doesNotThrow() {
        // Kills NegateConditionals on: reason != null ? reason : ""
        producer.publishLoanRejected("loan-3", "user-3", null);
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishLoanDisbursed_sendsToKafka() {
        producer.publishLoanDisbursed("loan-4", "user-4", "150000.00", "acct-2");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void allPublishMethods_eachCallKafkaSendOnce() {
        producer.publishLoanApplied("l", "u", "100", "PERSONAL");
        producer.publishLoanApproved("l", "u", "100", "a");
        producer.publishLoanRejected("l", "u", "reason");
        producer.publishLoanDisbursed("l", "u", "100", "a");
        verify(kafkaTemplate, times(4)).send(anyString(), anyString(), any());
    }
}