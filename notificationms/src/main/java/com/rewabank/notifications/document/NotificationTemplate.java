package com.rewabank.notifications.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notification_templates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationTemplate {

    @Id
    private String id;

    // e.g. ACCOUNT_ACTIVATED, TRANSACTION_COMPLETED
    @Indexed(unique = true)
    private String eventType;

    private String smsTemplate;    // "Dear {name}, your txn of ₹{amount} is done."
    private String emailSubject;
    private String emailTemplate;
    private String pushTitle;
    private String pushTemplate;

    private boolean smsEnabled;
    private boolean emailEnabled;
    private boolean pushEnabled;

    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
