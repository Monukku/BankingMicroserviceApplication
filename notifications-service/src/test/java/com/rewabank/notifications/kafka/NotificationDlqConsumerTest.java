package com.rewabank.notifications.kafka;

import com.rewabank.notifications.document.NotificationLog;
import com.rewabank.notifications.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDlqConsumerTest {

    @Mock NotificationLogRepository logRepository;
    @InjectMocks NotificationDlqConsumer consumer;

    @Test
    void handleDlqEvent_success_persistsFailedLogWithDlqSuffix() {
        // Kills VoidMethodCall on logRepository.save()
        // Kills NullReturn / EmptyObjectReturn on NotificationLog.builder().build()
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> event = Map.of(
                "eventId",        eventId,
                "eventType",      "ACCOUNT_CREATED",
                "keycloakUserId", "kc-user-1"
        );

        consumer.handleDlqEvent(event, "bank.account.created.dlq");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        NotificationLog saved = captor.getValue();

        assertThat(saved.getEventId()).isEqualTo(eventId + "-dlq");
        assertThat(saved.getEventType()).isEqualTo("ACCOUNT_CREATED");
        assertThat(saved.getKeycloakUserId()).isEqualTo("kc-user-1");
        assertThat(saved.getChannel()).isEqualTo("DLQ");
        assertThat(saved.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void handleDlqEvent_missingFields_usesDefaultValues() {
        // Kills NegateConditionals on getOrDefault fallbacks
        Map<String, Object> event = new HashMap<>();

        consumer.handleDlqEvent(event, "bank.fraud.alert.raised.dlq");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        NotificationLog saved = captor.getValue();

        assertThat(saved.getEventType()).isEqualTo("UNKNOWN");
        assertThat(saved.getKeycloakUserId()).isEqualTo("unknown");
        assertThat(saved.getEventId()).endsWith("-dlq");
    }

    @Test
    void handleDlqEvent_repositoryThrows_swallowsException() {
        // Kills VoidMethodCall / NegateConditionals in exception swallowing path
        doThrow(new RuntimeException("Mongo unavailable")).when(logRepository).save(any());

        assertThatCode(() -> consumer.handleDlqEvent(
                Map.of("eventId", "abc", "eventType", "TEST"),
                "bank.account.closed.dlq"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleDlqEvent_persistedLog_hasFailureReasonContainingTopic() {
        consumer.handleDlqEvent(
                Map.of("eventId", "ev-1", "eventType", "KYC_VERIFIED"),
                "bank.kyc.verified.dlq");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getFailureReason()).contains("bank.kyc.verified.dlq");
        assertThat(captor.getValue().getMessage()).contains("bank.kyc.verified.dlq");
    }
}