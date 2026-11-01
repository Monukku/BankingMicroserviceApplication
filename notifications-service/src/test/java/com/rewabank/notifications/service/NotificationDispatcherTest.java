package com.rewabank.notifications.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDispatcherTest {

    private final NotificationDispatcher dispatcher = new NotificationDispatcher();

    @BeforeEach
    void setUp() {
        // @Value fields are not injected when using new NotificationDispatcher()
        // Set simulationMode=true so dispatcher logs only and returns true
        ReflectionTestUtils.setField(dispatcher, "simulationMode", true);
        ReflectionTestUtils.setField(dispatcher, "smsProvider",   "log");
        ReflectionTestUtils.setField(dispatcher, "emailProvider", "log");
        ReflectionTestUtils.setField(dispatcher, "pushProvider",  "log");
    }

    @Test
    void sendSms_returnsTrue() {
        assertThat(dispatcher.sendSms("9876543210", "Your OTP is 123456")).isTrue();
    }

    @Test
    void sendEmail_returnsTrue() {
        assertThat(dispatcher.sendEmail(
                "rewa@rewabank.com", "Account Activated", "Hello Rewa, your account is active."))
                .isTrue();
    }

    @Test
    void sendPush_returnsTrue() {
        assertThat(dispatcher.sendPush("kc-user-1", "Account Active", "Tap to view details"))
                .isTrue();
    }

    @Test
    void sendSms_nullMobile_doesNotThrow() {
        assertThat(dispatcher.sendSms(null, "Test message")).isTrue();
    }

    @Test
    void sendEmail_nullEmail_doesNotThrow() {
        assertThat(dispatcher.sendEmail(null, "Subject", "Body")).isTrue();
    }

    @Test
    void sendSms_emptyMessage_doesNotThrow() {
        assertThat(dispatcher.sendSms("9876543210", "")).isTrue();
    }

    @Test
    void sendEmail_resendProvider_missingApiKey_returnsFalse() {
        // Real delivery requested via Resend, but no API key configured:
        // must fail (false), NOT fall through to the log-only default and return true.
        ReflectionTestUtils.setField(dispatcher, "simulationMode", false);
        ReflectionTestUtils.setField(dispatcher, "emailProvider", "resend");
        ReflectionTestUtils.setField(dispatcher, "resendApiKey", "");
        assertThat(dispatcher.sendEmail("rewa@rewabank.com", "Subject", "Body")).isFalse();
    }

    @Test
    void sendEmail_resendProvider_blowsUpGracefully_returnsFalse() {
        // Key present but delivery will fail (no network/invalid key in unit test):
        // exception must be swallowed and surfaced as false, never thrown.
        ReflectionTestUtils.setField(dispatcher, "simulationMode", false);
        ReflectionTestUtils.setField(dispatcher, "emailProvider", "resend");
        ReflectionTestUtils.setField(dispatcher, "resendApiKey", "re_test_invalid");
        ReflectionTestUtils.setField(dispatcher, "emailFromAddress", "noreply@rewabank.com");
        assertThat(dispatcher.sendEmail("rewa@rewabank.com", "Subject", "Body")).isFalse();
    }
}