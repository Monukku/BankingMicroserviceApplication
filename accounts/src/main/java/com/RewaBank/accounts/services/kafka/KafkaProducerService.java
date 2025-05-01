//package com.RewaBank.accounts.services.kafka;
//
//import com.RewaBank.accounts.dto.AccountsMessageDto;
//import com.RewaBank.accounts.entity.Accounts;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.cloud.stream.function.StreamBridge;
//import org.springframework.messaging.Message;
//import org.springframework.messaging.support.MessageBuilder;
//import org.springframework.stereotype.Service;
//
//@Service
//public class KafkaProducerService {
//    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
//    private final StreamBridge streamBridge;
//
//    public KafkaProducerService(StreamBridge streamBridge) {
//        this.streamBridge = streamBridge;
//    }
//
//    public boolean sendCommunication(Accounts account, Customer customer) {
//
//        // Validate account object
//        if (account.getAccountId() == null) {
//            throw new IllegalArgumentException("Partition key cannot be null.....");
//        }
//        // Create the message payload
//        var messageDto = new AccountsMessageDto(
//                account.getAccountId(), account.getAccountNumber(),
//                customer.getName(), customer.getEmail(), customer.getMobileNumber());
//
//        // Validate accountId
//        if (messageDto.accountId() == null) {
//            log.error("❌ ERROR: Cannot send message because accountId is null!");
//            return false;
//        }
//
//        // Build Kafka message with partition key
//        Message<AccountsMessageDto> message = MessageBuilder
//                .withPayload(messageDto)
//                .setHeader("accountId", messageDto.accountId())  // 🔥 Set partition key
//                .build();
//
//        // Send the message to Kafka
//        boolean result = streamBridge.send("sendCommunication-out-0", message);
//
//        if (!result) {
//            log.error("❌ ERROR: Failed to send message to Kafka for Account ID: {}", messageDto.accountId());
//        } else {
//            log.info("✅ Successfully sent message to Kafka with partition key: {}", messageDto.accountId());
//        }
//
//        return result;
//    }
//
//}