package com.rewabank.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.audit.entity.AuditLog;
import com.rewabank.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper       objectMapper;

    @Transactional
    public void record(Map<String, Object> event, String topic) {
        String eventId = (String) event.getOrDefault(
                "eventId", UUID.randomUUID().toString());

        // Idempotent — skip duplicate events
        if (auditLogRepository.existsByEventId(eventId)) {
            log.debug("Audit event already recorded — skipping: {}", eventId);
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);

            LocalDateTime occurredAt = null;
            try {
                String ts = (String) event.get("occurredAt");
                if (ts != null) occurredAt = LocalDateTime.parse(ts);
            } catch (Exception ignored) {}

            AuditLog entry = AuditLog.builder()
                    .eventId(eventId)
                    .eventType((String) event.getOrDefault("eventType", "UNKNOWN"))
                    .topic(topic)
                    .keycloakUserId((String) event.get("keycloakUserId"))
                    .aggregateId(extractAggregateId(event))
                    .aggregateType(extractAggregateType(event))
                    .payload(payload)
                    .ipAddress((String) event.get("ipAddress"))
                    .correlationId((String) event.get("correlationId"))
                    .occurredAt(occurredAt)
                    .recordedAt(LocalDateTime.now())
                    .build();

            auditLogRepository.save(entry);
            log.debug("Audit recorded: {} eventId: {}",
                    entry.getEventType(), eventId);

        } catch (Exception e) {
            log.error("Failed to record audit event {}: {}",
                    eventId, e.getMessage());
            throw new RuntimeException("Audit recording failed", e);
        }
    }

    private String extractAggregateId(Map<String, Object> event) {
        for (String key : new String[]{
                "accountId", "transactionId", "customerId",
                "loanId", "cardId", "userId"}) {
            Object val = event.get(key);
            if (val != null) return val.toString();
        }
        return null;
    }

    private String extractAggregateType(Map<String, Object> event) {
        String eventType = (String) event.getOrDefault("eventType", "");
        if (eventType.startsWith("ACCOUNT"))     return "Account";
        if (eventType.startsWith("TRANSACTION")) return "Transaction";
        if (eventType.startsWith("CUSTOMER"))    return "Customer";
        if (eventType.startsWith("LOAN"))        return "Loan";
        if (eventType.startsWith("CARD"))        return "Card";
        if (eventType.startsWith("USER"))        return "User";
        if (eventType.startsWith("FRAUD"))       return "FraudAlert";
        if (eventType.startsWith("PAYMENT"))     return "Payment";
        return "Unknown";
    }
}
