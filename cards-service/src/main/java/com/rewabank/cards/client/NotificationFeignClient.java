package com.rewabank.cards.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "notification-ms", url = "${feign.notification-ms.url:http://localhost:8094}")
public interface NotificationFeignClient {

    /**
     * Send notification to customer
     */
    @PostMapping("/api/v1/notifications/send")
    Map<String, Object> sendNotification(@RequestBody NotificationRequest request);

    /**
     * Notification request DTO
     */
    class NotificationRequest {
        public UUID customerId;
        public String notificationType;  // CARD_APPLIED, CARD_ACTIVATED, TRANSACTION_ALERT, etc.
        public String subject;
        public String message;
        public Map<String, String> parameters;  // Dynamic values for template
        public String channel;  // EMAIL, SMS, PUSH, ALL

        public NotificationRequest(UUID customerId, String notificationType, String subject,
                String message, Map<String, String> parameters, String channel) {
            this.customerId = customerId;
            this.notificationType = notificationType;
            this.subject = subject;
            this.message = message;
            this.parameters = parameters;
            this.channel = channel;
        }

        public NotificationRequest(UUID customerId, String notificationType, String subject,
                String message, String channel) {
            this(customerId, notificationType, subject, message, null, channel);
        }
    }
}

