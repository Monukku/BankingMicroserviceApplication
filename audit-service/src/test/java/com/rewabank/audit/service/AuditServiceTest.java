package com.rewabank.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.audit.entity.AuditLog;
import com.rewabank.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditServiceTest {

    @Mock AuditLogRepository auditLogRepository;
    @Mock ObjectMapper       objectMapper;

    @InjectMocks AuditService auditService;

    private static final String EVENT_ID   = "evt-audit-001";
    private static final String TOPIC      = "bank.account.activated";

    @BeforeEach
    void setUp() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":\"evt-audit-001\"}");
        when(auditLogRepository.existsByEventId(EVENT_ID)).thenReturn(false);
    }

    private Map<String, Object> baseEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId",        EVENT_ID);
        event.put("eventType",      "ACCOUNT_ACTIVATED");
        event.put("keycloakUserId", "kc-user-1");
        event.put("accountId",      "acc-123");
        event.put("occurredAt",     "2025-06-01T10:30:00");
        event.put("ipAddress",      "192.168.1.1");
        event.put("correlationId",  "corr-xyz");
        return event;
    }

    // ── record — happy path ───────────────────────────────────────

    @Test
    void record_newEvent_savesAuditLogWithAllFields() {
        auditService.record(baseEvent(), TOPIC);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getEventId()).isEqualTo(EVENT_ID);
        assertThat(saved.getEventType()).isEqualTo("ACCOUNT_ACTIVATED");
        assertThat(saved.getTopic()).isEqualTo(TOPIC);
        assertThat(saved.getKeycloakUserId()).isEqualTo("kc-user-1");
        assertThat(saved.getAggregateId()).isEqualTo("acc-123");
        assertThat(saved.getAggregateType()).isEqualTo("Account");
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-xyz");
        assertThat(saved.getRecordedAt()).isNotNull();
    }

    @Test
    void record_occurredAt_parsedFromPayload() {
        auditService.record(baseEvent(), TOPIC);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt())
                .isEqualTo(LocalDateTime.of(2025, 6, 1, 10, 30, 0));
    }

    @Test
    void record_invalidOccurredAt_savesWithNullOccurredAt() {
        Map<String, Object> event = baseEvent();
        event.put("occurredAt", "not-a-date");

        auditService.record(event, TOPIC);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt()).isNull();
    }

    @Test
    void record_missingOccurredAt_savesWithNullOccurredAt() {
        Map<String, Object> event = baseEvent();
        event.remove("occurredAt");

        auditService.record(event, TOPIC);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt()).isNull();
    }

    // ── idempotency ───────────────────────────────────────────────

    @Test
    void record_duplicateEventId_skipsWithoutSaving() {
        when(auditLogRepository.existsByEventId(EVENT_ID)).thenReturn(true);

        auditService.record(baseEvent(), TOPIC);

        verify(auditLogRepository, never()).save(any());
        verifyNoInteractions(objectMapper);
    }

    @Test
    void record_missingEventId_generatesUuidAndSaves() {
        Map<String, Object> event = baseEvent();
        event.remove("eventId");
        when(auditLogRepository.existsByEventId(anyString())).thenReturn(false);

        auditService.record(event, TOPIC);

        verify(auditLogRepository, never()).existsByEventId(EVENT_ID);
        verify(auditLogRepository).existsByEventId(
                argThat(id -> id != null && !id.isBlank()));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ── event field defaults ──────────────────────────────────────

    @Test
    void record_missingEventType_defaultsToUnknown() {
        Map<String, Object> event = baseEvent();
        event.remove("eventType");

        auditService.record(event, TOPIC);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("UNKNOWN");
    }

    // ── aggregateId extraction ────────────────────────────────────

    @Test
    void record_aggregateId_picksAccountId() {
        auditService.record(baseEvent(), TOPIC);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAggregateId()).isEqualTo("acc-123");
    }

    @Test
    void record_aggregateId_picksTransactionId_whenNoAccountId() {
        Map<String, Object> event = baseEvent();
        event.remove("accountId");
        event.put("transactionId", "txn-456");

        auditService.record(event, TOPIC);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAggregateId()).isEqualTo("txn-456");
    }

    @Test
    void record_aggregateId_noKnownKey_returnsNull() {
        Map<String, Object> event = baseEvent();
        event.remove("accountId");

        auditService.record(event, TOPIC);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAggregateId()).isNull();
    }

    // ── aggregateType extraction ──────────────────────────────────

    @Test
    void record_aggregateType_accountPrefix() {
        assertAggregateType("ACCOUNT_ACTIVATED", "Account");
    }

    @Test
    void record_aggregateType_transactionPrefix() {
        assertAggregateType("TRANSACTION_COMPLETED", "Transaction");
    }

    @Test
    void record_aggregateType_loanPrefix() {
        assertAggregateType("LOAN_APPROVED", "Loan");
    }

    @Test
    void record_aggregateType_cardPrefix() {
        assertAggregateType("CARD_ISSUED", "Card");
    }

    @Test
    void record_aggregateType_fraudPrefix() {
        assertAggregateType("FRAUD_ALERT_RAISED", "FraudAlert");
    }

    @Test
    void record_aggregateType_userPrefix() {
        assertAggregateType("USER_REGISTERED", "User");
    }

    @Test
    void record_aggregateType_unknownPrefix() {
        assertAggregateType("OTP_SENT", "Unknown");
    }

    private void assertAggregateType(String eventType, String expectedType) {
        Map<String, Object> event = baseEvent();
        event.put("eventType", eventType);
        String uniqueEventId = UUID.randomUUID().toString();
        event.put("eventId", uniqueEventId);
        when(auditLogRepository.existsByEventId(uniqueEventId)).thenReturn(false);

        auditService.record(event, TOPIC);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, atLeastOnce()).save(captor.capture());
        AuditLog last = captor.getAllValues().getLast();
        assertThat(last.getAggregateType()).isEqualTo(expectedType);
    }

    // ── serialization failure ─────────────────────────────────────

    @Test
    void record_serializationFails_throwsRuntimeException() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("JSON error") {});

        assertThatThrownBy(() -> auditService.record(baseEvent(), TOPIC))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Audit recording failed");

        verify(auditLogRepository, never()).save(any());
    }
}