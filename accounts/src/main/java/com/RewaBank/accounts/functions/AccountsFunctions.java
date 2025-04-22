package com.RewaBank.accounts.functions;

import com.RewaBank.accounts.dto.AccountsMessageDto;
import com.RewaBank.accounts.services.IAccountsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
public class AccountsFunctions {
    private static final Logger log = LoggerFactory.getLogger(AccountsFunctions.class);
    private final IAccountsService iAccountsService;

    public AccountsFunctions(IAccountsService accountsService) {
        this.iAccountsService = accountsService;
    }

    @Bean
    public Consumer<Message<AccountsMessageDto>> updateCommunication() {
        return message -> {
            // Extract partition info
            Integer partition = message.getHeaders().get(KafkaHeaders.RECEIVED_PARTITION, Integer.class);
            log.info("📩 Received Kafka message from Partition: {}", partition);

            // Extract payload
            AccountsMessageDto payload = message.getPayload();

            if (payload.accountId() == null) {
                log.error("❌ ERROR: Account ID is null in the Kafka message payload!");
                return;
            }

            log.info("🔄 Processing communication update for Account ID: {}", payload.accountId());

            // Update communication status
            try {
                iAccountsService.updateCommunicationStatus(payload.accountId());
                log.info("✅ Successfully updated communication status for Account ID: {}", payload.accountId());
            } catch (Exception e) {
                log.error("❌ ERROR: Failed to update communication status for Account ID: {}", payload.accountId(), e);
            }
        };
    }
}


