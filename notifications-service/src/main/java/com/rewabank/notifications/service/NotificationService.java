package com.rewabank.notifications.service;

import com.rewabank.notifications.document.NotificationLog;
import com.rewabank.notifications.document.NotificationTemplate;
import com.rewabank.notifications.repository.NotificationLogRepository;
import com.rewabank.notifications.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository      logRepository;
    private final NotificationDispatcher         dispatcher;

    public void processEvent(Map<String, Object> event) {
        String eventType = (String) event.get("eventType");
        String eventId   = (String) event.getOrDefault(
                "eventId", UUID.randomUUID().toString());
        String userId    = (String) event.getOrDefault(
                "keycloakUserId", "unknown");

        // Idempotent — skip if already processed
        if (logRepository.existsByEventId(eventId)) {
            log.debug("Event already processed — skipping: {}", eventId);
            return;
        }

        Optional<NotificationTemplate> templateOpt =
                templateRepository.findByEventType(eventType);

        if (templateOpt.isEmpty()) {
            log.debug("No template for event type: {} — skipping", eventType);
            return;
        }

        NotificationTemplate template = templateOpt.get();
        String message = buildMessage(template.getSmsTemplate(), event);

        // SMS
        if (template.isSmsEnabled()) {
            String mobile = (String) event.getOrDefault("mobileNumber", "");
            boolean sent = dispatcher.sendSms(mobile, message);
            saveLog(eventId, eventType, userId, "SMS",
                    mobile, message, sent ? "SENT" : "FAILED", null);
        }

        // Email
        if (template.isEmailEnabled()) {
            String email = (String) event.getOrDefault("email", "");
            String subject = buildMessage(template.getEmailSubject(), event);
            String body = buildMessage(template.getEmailTemplate(), event);
            boolean sent = dispatcher.sendEmail(email, subject, body);
            saveLog(eventId, eventType, userId, "EMAIL",
                    email, subject, sent ? "SENT" : "FAILED", null);
        }

        // Push
        if (template.isPushEnabled()) {
            String title = buildMessage(template.getPushTitle(), event);
            String body = buildMessage(template.getPushTemplate(), event);
            boolean sent = dispatcher.sendPush(userId, title, body);
            saveLog(eventId, eventType, userId, "PUSH",
                    userId, title, sent ? "SENT" : "FAILED", null);
        }
    }

    // Template substitution: {key} → value from event map
    private String buildMessage(String template, Map<String, Object> event) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : event.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace(
                        "{" + entry.getKey() + "}",
                        entry.getValue().toString());
            }
        }
        // Warn if any placeholders remain — indicates template/event mismatch
        if (result.contains("{") && result.contains("}")) {
            log.warn("Unreplaced placeholders in notification message — " +
                    "eventType: {} message: {}", event.get("eventType"), result);
        }
        return result;
    }

    private void saveLog(String eventId, String eventType,
                         String userId, String channel,
                         String recipient, String message,
                         String status, String failureReason) {
        NotificationLog log = NotificationLog.builder()
                .eventId(eventId)
                .eventType(eventType)
                .keycloakUserId(userId)
                .channel(channel)
                .recipient(recipient)
                .message(message)
                .status(status)
                .failureReason(failureReason)
                .sentAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        logRepository.save(log);
    }
}
