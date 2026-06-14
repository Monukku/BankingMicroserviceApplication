package com.rewabank.transactions.kafka;

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
class TransactionEventProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks TransactionEventProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReflectionTestUtils.setField(producer, "initiatedTopic",  "bank.transaction.initiated");
        ReflectionTestUtils.setField(producer, "completedTopic",  "bank.transaction.completed");
        ReflectionTestUtils.setField(producer, "failedTopic",     "bank.transaction.failed");
        ReflectionTestUtils.setField(producer, "reversedTopic",   "bank.transaction.reversed");
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<>());
    }

    @Test
    void publishInitiated_sendsToKafka() {
        producer.publishInitiated("txn-1", "ACC001", "ACC002", "1000.00", "TRANSFER");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishCompleted_withReferenceNumber_sendsToKafka() {
        producer.publishCompleted("txn-2", "ACC001", "ACC002", "500.00", "REF-XYZ");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishCompleted_nullReferenceNumber_doesNotThrow() {
        // Kills NegateConditionals on: referenceNumber != null ? referenceNumber : ""
        producer.publishCompleted("txn-2", "ACC001", "ACC002", "500.00", null);
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishFailed_withReason_sendsToKafka() {
        producer.publishFailed("txn-3", "Insufficient funds", "ACC001");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishFailed_nullReason_doesNotThrow() {
        // Kills NegateConditionals on: reason != null ? reason : ""
        producer.publishFailed("txn-3", null, "ACC001");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishReversed_withReason_sendsToKafka() {
        producer.publishReversed("txn-4", "Fraud reversal", "ACC001", "1000.00");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishReversed_nullReason_doesNotThrow() {
        // Kills NegateConditionals on: reason != null ? reason : ""
        producer.publishReversed("txn-4", null, "ACC001", "1000.00");
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void allPublishMethods_eachCallKafkaSendOnce() {
        producer.publishInitiated("t", "A", "B", "100", "TRANSFER");
        producer.publishCompleted("t", "A", "B", "100", "REF");
        producer.publishFailed("t", "reason", "A");
        producer.publishReversed("t", "reason", "A", "100");
        verify(kafkaTemplate, times(4)).send(anyString(), anyString(), any());
    }
}
