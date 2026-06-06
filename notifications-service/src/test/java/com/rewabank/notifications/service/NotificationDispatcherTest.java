package com.rewabank.notifications.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDispatcherTest {

    private final NotificationDispatcher dispatcher = new NotificationDispatcher();

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
}