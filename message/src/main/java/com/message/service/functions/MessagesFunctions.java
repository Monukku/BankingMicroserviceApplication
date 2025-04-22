package com.message.service.functions;

import com.message.service.dto.AccountsMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Objects;
import java.util.function.Function;

@Configuration
public class MessagesFunctions {
    private static final Logger log = LoggerFactory.getLogger(MessagesFunctions.class);

    @Bean
    public Function<Message<AccountsMessageDto>, Message<AccountsMessageDto>> email() {
        return message -> {
            MessageHeaders headers = message.getHeaders();
            String accountId = headers.containsKey("accountId") ? Objects.requireNonNull(headers.get("accountId")).toString() : null;

            log.info("📧 Received email message: {}, Account ID: {}", message.getPayload(), accountId);

            if (accountId == null) {
                log.error("❌ ERROR: Missing 'accountId' in headers!");
                throw new IllegalArgumentException("Account ID is missing in headers!");
            }

            // Simulate sending email
            log.info("📧 Sending email to: {}", message.getPayload().email());

            // Return the same message as confirmation
            return MessageBuilder.withPayload(message.getPayload())
                    .setHeader("accountId", accountId)  // Set partition key
                    .build();
        };
    }

    @Bean
    public Function<Message<AccountsMessageDto>, Message<AccountsMessageDto>> sms() {
        return message -> {
            MessageHeaders headers = message.getHeaders();
            String accountId = headers.containsKey("accountId") ? Objects.requireNonNull(headers.get("accountId")).toString() : null;

            log.info("📱 Received SMS message: {}, Account ID: {}", message.getPayload(), accountId);

            if (accountId == null) {
                log.error("❌ ERROR: Missing 'accountId' in headers!");
                throw new IllegalArgumentException("Account ID is missing in headers!");
            }

            // Simulate sending SMS
            log.info("📱 Sending SMS to: {}", message.getPayload().mobileNumber());

            // Return the same message as confirmation
            return MessageBuilder.withPayload(message.getPayload())
                    .setHeader("accountId", accountId)  // Set partition key
                    .build();
        };
    }
}