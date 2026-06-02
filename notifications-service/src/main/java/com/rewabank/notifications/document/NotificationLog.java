package com.rewabank.notifications.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notification_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLog {

    @Id
    private String id;

    @Indexed
    private String keycloakUserId;

    private String eventType;
    private String channel;         // SMS, EMAIL, PUSH

    @Indexed
    private String eventId;         // from Kafka event

    private String recipient;       // masked mobile or email
    private String message;

    private String status;          // SENT, FAILED, SKIPPED
    private String failureReason;

    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
