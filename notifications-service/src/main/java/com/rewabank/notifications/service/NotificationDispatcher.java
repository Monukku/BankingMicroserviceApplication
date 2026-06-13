package com.rewabank.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Notification delivery dispatcher.
 *
 * Provider wiring:
 *   SMS   — set SMS_PROVIDER=twilio | aws-sns + supply credentials
 *   Email — set EMAIL_PROVIDER=smtp | aws-ses | sendgrid + supply credentials
 *   Push  — set PUSH_PROVIDER=fcm + supply FCM_SERVER_KEY
 *
 * NOTIFICATION_SIMULATION_MODE=true (default) — logs only, no real delivery.
 * Set to false in production with real provider credentials.
 */
@Service
@Slf4j
public class NotificationDispatcher {

    @Value("${notification.simulation.mode:true}")
    private boolean simulationMode;

    @Value("${notification.sms.provider:log}")
    private String smsProvider;

    @Value("${notification.sms.twilio-account-sid:}")
    private String twilioAccountSid;

    @Value("${notification.sms.twilio-auth-token:}")
    private String twilioAuthToken;

    @Value("${notification.sms.twilio-from-number:}")
    private String twilioFromNumber;

    @Value("${notification.email.provider:log}")
    private String emailProvider;

    @Value("${notification.email.from-address:noreply@rewabank.com}")
    private String emailFromAddress;

    @Value("${notification.email.sendgrid-api-key:}")
    private String sendgridApiKey;

    @Value("${notification.push.provider:log}")
    private String pushProvider;

    @Value("${notification.push.fcm-server-key:}")
    private String fcmServerKey;

    // ── SMS ───────────────────────────────────────────────────────────────────

    public boolean sendSms(String mobileNumber, String message) {
        if (simulationMode) {
            log.info("[SIMULATION] SMS → {} : {}", maskMobile(mobileNumber), message);
            return true;
        }
        try {
            return switch (smsProvider.toLowerCase()) {
                case "twilio"  -> sendViaTwilio(mobileNumber, message);
                case "aws-sns" -> sendViaAwsSns(mobileNumber, message);
                default -> {
                    log.warn("Unknown SMS provider '{}' — logging only", smsProvider);
                    log.info("SMS → {} : {}", maskMobile(mobileNumber), message);
                    yield true;
                }
            };
        } catch (Exception e) {
            log.error("SMS dispatch failed to {}: {}", maskMobile(mobileNumber), e.getMessage());
            return false;
        }
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    public boolean sendEmail(String email, String subject, String body) {
        if (simulationMode) {
            log.info("[SIMULATION] EMAIL → {} subject: {}", maskEmail(email), subject);
            return true;
        }
        try {
            return switch (emailProvider.toLowerCase()) {
                case "sendgrid" -> sendViaSendGrid(email, subject, body);
                case "smtp"     -> sendViaSmtp(email, subject, body);
                case "aws-ses"  -> sendViaAwsSes(email, subject, body);
                default -> {
                    log.warn("Unknown email provider '{}' — logging only", emailProvider);
                    log.info("EMAIL → {} subject: {}", maskEmail(email), subject);
                    yield true;
                }
            };
        } catch (Exception e) {
            log.error("Email dispatch failed to {}: {}", maskEmail(email), e.getMessage());
            return false;
        }
    }

    // ── Push ──────────────────────────────────────────────────────────────────

    public boolean sendPush(String userId, String title, String body) {
        if (simulationMode) {
            log.info("[SIMULATION] PUSH → {} title: {}", userId, title);
            return true;
        }
        try {
            return switch (pushProvider.toLowerCase()) {
                case "fcm"  -> sendViaFcm(userId, title, body);
                default -> {
                    log.warn("Unknown push provider '{}' — logging only", pushProvider);
                    log.info("PUSH → {} title: {}", userId, title);
                    yield true;
                }
            };
        } catch (Exception e) {
            log.error("Push dispatch failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    // ── Provider implementations ──────────────────────────────────────────────

    private boolean sendViaTwilio(String to, String message) {
        if (twilioAccountSid.isBlank() || twilioAuthToken.isBlank()) {
            log.error("Twilio credentials not configured — set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN");
            return false;
        }
        String body = "To=" + encode(to)
                + "&From=" + encode(twilioFromNumber)
                + "&Body=" + encode(message);

        RestClient client = RestClient.builder()
                .baseUrl("https://api.twilio.com")
                .defaultHeaders(h -> h.setBasicAuth(twilioAccountSid, twilioAuthToken))
                .build();

        var response = client.post()
                .uri("/2010-04-01/Accounts/{sid}/Messages.json", twilioAccountSid)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
                .retrieve()
                .toBodilessEntity();

        boolean ok = response.getStatusCode().is2xxSuccessful();
        log.info("Twilio SMS to {} — {}", maskMobile(to), response.getStatusCode());
        return ok;
    }

    private boolean sendViaAwsSns(String to, String message) {
        // Wire: software.amazon.awssdk:sns + SnsClient.publish()
        log.warn("AWS SNS not wired (to={}, msgLen={}) — add software.amazon.awssdk:sns to pom.xml",
                maskMobile(to), message == null ? 0 : message.length());
        return false;
    }

    private boolean sendViaSmtp(String to, String subject, String body) {
        // Wire: spring-boot-starter-mail + JavaMailSender
        log.warn("SMTP not wired (to={}, subject={}) — add spring-boot-starter-mail to pom.xml and configure spring.mail.*",
                maskEmail(to), subject);
        return false;
    }

    private boolean sendViaAwsSes(String to, String subject, String body) {
        // Wire: software.amazon.awssdk:sesv2 + SesV2Client.sendEmail()
        log.warn("AWS SES not wired (to={}, subject={}) — add software.amazon.awssdk:sesv2 to pom.xml",
                maskEmail(to), subject);
        return false;
    }

    private boolean sendViaFcm(String userId, String title, String body) {
        if (fcmServerKey.isBlank()) {
            log.error("FCM server key not configured — set FCM_SERVER_KEY");
            return false;
        }
        Map<String, Object> payload = Map.of(
                "to", "/topics/user-" + userId,
                "notification", Map.of("title", title, "body", body)
        );

        RestClient client = RestClient.builder()
                .baseUrl("https://fcm.googleapis.com")
                .build();

        var response = client.post()
                .uri("/fcm/send")
                .header("Authorization", "key=" + fcmServerKey)
                .header("Content-Type", "application/json")
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        boolean ok = response.getStatusCode().is2xxSuccessful();
        log.info("FCM push to user {} — {}", userId, response.getStatusCode());
        return ok;
    }

    private boolean sendViaSendGrid(String to, String subject, String body) {
        if (sendgridApiKey.isBlank()) {
            log.error("SendGrid API key not configured — set SENDGRID_API_KEY");
            return false;
        }
        Map<String, Object> payload = Map.of(
                "personalizations", List.of(
                        Map.of("to", List.of(Map.of("email", to)))),
                "from", Map.of("email", emailFromAddress),
                "subject", subject,
                "content", List.of(Map.of("type", "text/plain", "value", body))
        );

        RestClient client = RestClient.builder()
                .baseUrl("https://api.sendgrid.com")
                .defaultHeader("Authorization", "Bearer " + sendgridApiKey)
                .build();

        var response = client.post()
                .uri("/v3/mail/send")
                .header("Content-Type", "application/json")
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        boolean ok = response.getStatusCode().is2xxSuccessful();
        log.info("SendGrid email to {} — {}", maskEmail(to), response.getStatusCode());
        return ok;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) return "XXXX";
        return "XXXXXXX" + mobile.substring(mobile.length() - 3);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "XXXX";
        String[] parts = email.split("@");
        return parts[0].charAt(0) + "****@" + parts[1];
    }

    private String encode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }
}
