package com.rewabank.audit.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "audit_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    @Indexed
    private String eventType;

    private String topic;

    @Indexed
    private String keycloakUserId;

    @Indexed
    private String aggregateId;

    private String aggregateType;

    // Full event payload — immutable record
    private String payload;

    private String ipAddress;
    private String correlationId;

    private LocalDateTime occurredAt;

    @Indexed
    private LocalDateTime recordedAt;
}