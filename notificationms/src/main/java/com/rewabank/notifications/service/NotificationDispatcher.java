package com.rewabank.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Pluggable dispatcher for SMS, Email, Push.
 * In production: wire real SMS gateway (Twilio/AWS SNS),
 * email provider (SES/SendGrid), FCM for push.
 * Currently logs to simulate dispatch.
 */
@Service
@Slf4j
public class NotificationDispatcher {

    public boolean sendSms(String mobileNumber, String message) {
        // TODO: wire Twilio / AWS SNS
        log.info("SMS → {} : {}", maskMobile(mobileNumber), message);
        return true;
    }

    public boolean sendEmail(String email, String subject, String body) {
        // TODO: wire AWS SES / SendGrid
        log.info("EMAIL → {} subject: {}", maskEmail(email), subject);
        return true;
    }

    public boolean sendPush(String userId, String title, String body) {
        // TODO: wire FCM / APNs
        log.info("PUSH → {} title: {}", userId, title);
        return true;
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) return "XXXX";
        return "XXXXXXX" + mobile.substring(mobile.length() - 3);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "XXXX";
        String[] parts = email.split("@");
        return parts[0].charAt(0) + "****@" + parts[1];
    }
}
