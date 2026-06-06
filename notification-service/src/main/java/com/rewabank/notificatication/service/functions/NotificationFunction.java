package com.rewabank.notificatication.service.functions;

import com.rewabank.notificatication.service.Service.EmailService;
import com.rewabank.notificatication.service.Service.SmsService;
import com.rewabank.notificatication.service.dto.AccountsMessageDto;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class NotificationFunction {

    private final EmailService emailService;
    private final SmsService smsService;

    public NotificationFunction(EmailService emailService, SmsService smsService) {
        this.emailService = emailService;
        this.smsService = smsService;
    }

    @StreamListener("emailsms-in-0")
    public void handleNotification(@Payload AccountsMessageDto message) {
        try {
            emailService.sendEmail(message.email(), "Notification", "Details: " + message);
            smsService.sendSms(message.mobileNumber(), "Notification: " + message);
        } catch (Exception e) {
            e.printStackTrace(); // Log the error
        }
    }
}
