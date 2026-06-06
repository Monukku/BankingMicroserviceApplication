package com.rewabank.notifications.service;

import com.rewabank.notifications.document.NotificationLog;
import com.rewabank.notifications.document.NotificationTemplate;
import com.rewabank.notifications.repository.NotificationLogRepository;
import com.rewabank.notifications.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock NotificationTemplateRepository templateRepository;
    @Mock NotificationLogRepository      logRepository;
    @Mock NotificationDispatcher         dispatcher;

    @InjectMocks NotificationService notificationService;

    private static final String EVENT_ID   = "evt-abc-123";
    private static final String EVENT_TYPE = "ACCOUNT_ACTIVATED";
    private static final String USER_ID    = "kc-user-1";

    private Map<String, Object> baseEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId",        EVENT_ID);
        event.put("eventType",      EVENT_TYPE);
        event.put("keycloakUserId", USER_ID);
        event.put("mobileNumber",   "9876543210");
        event.put("email",          "rewa@rewabank.com");
        event.put("name",           "Rewa Test");
        return event;
    }

    private NotificationTemplate smsTemplate() {
        return NotificationTemplate.builder()
                .eventType(EVENT_TYPE)
                .smsTemplate("Dear {name}, your account is now active.")
                .smsEnabled(true)
                .emailEnabled(false)
                .pushEnabled(false)
                .build();
    }

    private NotificationTemplate emailTemplate() {
        return NotificationTemplate.builder()
                .eventType(EVENT_TYPE)
                .emailSubject("Account Activated")
                .emailTemplate("Hello {name}, your account is active.")
                .smsEnabled(false)
                .emailEnabled(true)
                .pushEnabled(false)
                .build();
    }

    private NotificationTemplate pushTemplate() {
        return NotificationTemplate.builder()
                .eventType(EVENT_TYPE)
                .pushTitle("Account Active")
                .pushTemplate("Your account is now active, {name}.")
                .smsEnabled(false)
                .emailEnabled(false)
                .pushEnabled(true)
                .build();
    }

    private NotificationTemplate allChannelsTemplate() {
        return NotificationTemplate.builder()
                .eventType(EVENT_TYPE)
                .smsTemplate("SMS: {name}")
                .emailSubject("Email subject")
                .emailTemplate("Email body: {name}")
                .pushTitle("Push title")
                .pushTemplate("Push body")
                .smsEnabled(true)
                .emailEnabled(true)
                .pushEnabled(true)
                .build();
    }

    @BeforeEach
    void setUp() {
        when(logRepository.existsByEventId(EVENT_ID)).thenReturn(false);
    }

    // ── channel dispatch ─────────────────────────────────────────

    @Test
    void smsOnly_dispatchesSmsAndSavesLog() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.of(smsTemplate()));
        when(dispatcher.sendSms(anyString(), anyString())).thenReturn(true);

        notificationService.processEvent(baseEvent());

        verify(dispatcher).sendSms(eq("9876543210"), contains("Rewa Test"));
        verify(dispatcher, never()).sendEmail(any(), any(), any());
        verify(dispatcher, never()).sendPush(any(), any(), any());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        NotificationLog saved = logCaptor.getValue();
        assertThat(saved.getChannel()).isEqualTo("SMS");
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getEventId()).isEqualTo(EVENT_ID);
        assertThat(saved.getKeycloakUserId()).isEqualTo(USER_ID);
    }

    @Test
    void emailOnly_dispatchesEmailAndSavesLog() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.of(emailTemplate()));
        when(dispatcher.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);

        notificationService.processEvent(baseEvent());

        verify(dispatcher).sendEmail(eq("rewa@rewabank.com"), eq("Account Activated"), contains("Rewa Test"));
        verify(dispatcher, never()).sendSms(any(), any());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getChannel()).isEqualTo("EMAIL");
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SENT");
    }

    @Test
    void pushOnly_dispatchesPushAndSavesLog() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.of(pushTemplate()));
        when(dispatcher.sendPush(anyString(), anyString(), anyString())).thenReturn(true);

        notificationService.processEvent(baseEvent());

        verify(dispatcher).sendPush(eq(USER_ID), eq("Account Active"), contains("Rewa Test"));
        verify(dispatcher, never()).sendSms(any(), any());
        verify(dispatcher, never()).sendEmail(any(), any(), any());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getChannel()).isEqualTo("PUSH");
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SENT");
    }

    @Test
    void allChannels_dispatchesAllThreeAndSavesThreeLogs() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.of(allChannelsTemplate()));
        when(dispatcher.sendSms(any(), any())).thenReturn(true);
        when(dispatcher.sendEmail(any(), any(), any())).thenReturn(true);
        when(dispatcher.sendPush(any(), any(), any())).thenReturn(true);

        notificationService.processEvent(baseEvent());

        verify(dispatcher).sendSms(any(), any());
        verify(dispatcher).sendEmail(any(), any(), any());
        verify(dispatcher).sendPush(any(), any(), any());
        verify(logRepository, times(3)).save(any(NotificationLog.class));
    }

    // ── idempotency ──────────────────────────────────────────────

    @Test
    void duplicateEventId_skipsAllProcessing() {
        when(logRepository.existsByEventId(EVENT_ID)).thenReturn(true);

        notificationService.processEvent(baseEvent());

        verify(templateRepository, never()).findByEventType(any());
        verify(dispatcher, never()).sendSms(any(), any());
        verify(dispatcher, never()).sendEmail(any(), any(), any());
        verify(dispatcher, never()).sendPush(any(), any(), any());
        verify(logRepository, never()).save(any());
    }

    // ── missing template ─────────────────────────────────────────

    @Test
    void noTemplateForEventType_skipsAllDispatching() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.empty());

        notificationService.processEvent(baseEvent());

        verify(dispatcher, never()).sendSms(any(), any());
        verify(dispatcher, never()).sendEmail(any(), any(), any());
        verify(dispatcher, never()).sendPush(any(), any(), any());
        verify(logRepository, never()).save(any());
    }

    // ── template substitution ────────────────────────────────────

    @Test
    void templateSubstitution_smsPlaceholdersReplaced() {
        NotificationTemplate t = NotificationTemplate.builder()
                .eventType(EVENT_TYPE)
                .smsTemplate("Hi {name}, txn ₹{amount} done on account {accountNumber}.")
                .smsEnabled(true)
                .emailEnabled(false)
                .pushEnabled(false)
                .build();
        when(templateRepository.findByEventType(EVENT_TYPE)).thenReturn(Optional.of(t));
        when(dispatcher.sendSms(anyString(), anyString())).thenReturn(true);

        Map<String, Object> event = baseEvent();
        event.put("amount", "5000");
        event.put("accountNumber", "ACC1234567890");

        notificationService.processEvent(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).sendSms(anyString(), msgCaptor.capture());
        assertThat(msgCaptor.getValue())
                .contains("Rewa Test")
                .contains("5000")
                .contains("ACC1234567890")
                .doesNotContain("{name}")
                .doesNotContain("{amount}")
                .doesNotContain("{accountNumber}");
    }

    @Test
    void templateSubstitution_emailSubjectAndBodyReplaced() {
        NotificationTemplate t = NotificationTemplate.builder()
                .eventType(EVENT_TYPE)
                .emailSubject("Hello {name}")
                .emailTemplate("Your balance is {balance}.")
                .smsEnabled(false)
                .emailEnabled(true)
                .pushEnabled(false)
                .build();
        when(templateRepository.findByEventType(EVENT_TYPE)).thenReturn(Optional.of(t));
        when(dispatcher.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);

        Map<String, Object> event = baseEvent();
        event.put("balance", "12000.00");

        notificationService.processEvent(event);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor    = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).sendEmail(anyString(), subjectCaptor.capture(), bodyCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo("Hello Rewa Test");
        assertThat(bodyCaptor.getValue()).contains("12000.00").doesNotContain("{balance}");
    }

    @Test
    void templateSubstitution_unknownPlaceholdersLeftAsIs() {
        NotificationTemplate t = NotificationTemplate.builder()
                .eventType(EVENT_TYPE)
                .smsTemplate("Code: {otp}")
                .smsEnabled(true)
                .emailEnabled(false)
                .pushEnabled(false)
                .build();
        when(templateRepository.findByEventType(EVENT_TYPE)).thenReturn(Optional.of(t));
        when(dispatcher.sendSms(anyString(), anyString())).thenReturn(true);

        // event does NOT contain "otp"
        notificationService.processEvent(baseEvent());

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).sendSms(anyString(), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).contains("{otp}");
    }

    @Test
    void nullSmsTemplate_sendsEmptyString() {
        NotificationTemplate t = NotificationTemplate.builder()
                .eventType(EVENT_TYPE)
                .smsTemplate(null)
                .smsEnabled(true)
                .emailEnabled(false)
                .pushEnabled(false)
                .build();
        when(templateRepository.findByEventType(EVENT_TYPE)).thenReturn(Optional.of(t));
        when(dispatcher.sendSms(anyString(), anyString())).thenReturn(true);

        notificationService.processEvent(baseEvent());

        verify(dispatcher).sendSms(anyString(), eq(""));
    }

    // ── dispatcher failure → FAILED log ─────────────────────────

    @Test
    void smsFails_logsFailedStatus() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.of(smsTemplate()));
        when(dispatcher.sendSms(anyString(), anyString())).thenReturn(false);

        notificationService.processEvent(baseEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void emailFails_logsFailedStatus() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.of(emailTemplate()));
        when(dispatcher.sendEmail(anyString(), anyString(), anyString())).thenReturn(false);

        notificationService.processEvent(baseEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void pushFails_logsFailedStatus() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.of(pushTemplate()));
        when(dispatcher.sendPush(anyString(), anyString(), anyString())).thenReturn(false);

        notificationService.processEvent(baseEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("FAILED");
    }

    // ── event field defaults ─────────────────────────────────────

    @Test
    void missingKeycloakUserId_logsAsUnknown() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.of(smsTemplate()));
        when(dispatcher.sendSms(anyString(), anyString())).thenReturn(true);

        Map<String, Object> event = baseEvent();
        event.remove("keycloakUserId");

        notificationService.processEvent(event);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getKeycloakUserId()).isEqualTo("unknown");
    }

    @Test
    void missingEventId_generatesUuidForIdempotencyCheck() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.of(smsTemplate()));
        when(dispatcher.sendSms(anyString(), anyString())).thenReturn(true);

        Map<String, Object> event = baseEvent();
        event.remove("eventId");

        notificationService.processEvent(event);

        // existsByEventId must be called with some generated string (not the fixed EVENT_ID)
        verify(logRepository, never()).existsByEventId(EVENT_ID);
        verify(logRepository).existsByEventId(argThat(id -> id != null && !id.isBlank()));
    }

    @Test
    void logRecord_containsCorrectFields() {
        when(templateRepository.findByEventType(EVENT_TYPE))
                .thenReturn(Optional.of(smsTemplate()));
        when(dispatcher.sendSms(anyString(), anyString())).thenReturn(true);

        notificationService.processEvent(baseEvent());

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        NotificationLog log = logCaptor.getValue();
        assertThat(log.getEventId()).isEqualTo(EVENT_ID);
        assertThat(log.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(log.getChannel()).isEqualTo("SMS");
        assertThat(log.getRecipient()).isEqualTo("9876543210");
        assertThat(log.getSentAt()).isNotNull();
        assertThat(log.getCreatedAt()).isNotNull();
    }
}