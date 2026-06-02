package com.rewabank.cards.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.card-issued:bank.card.issued}")
    private String cardIssuedTopic;

    @Value("${kafka.topics.card-blocked:bank.card.blocked}")
    private String cardBlockedTopic;

    public void publishCardIssued(String cardId, String userId,
                                  String accountId, String cardType,
                                  String lastFour) {
        publish(cardIssuedTopic, cardId, Map.of(
                "eventId",   UUID.randomUUID().toString(),
                "eventType", "CARD_ISSUED",
                "cardId",    cardId,
                "userId",    userId,
                "accountId", accountId,
                "cardType",  cardType,
                "lastFour",  lastFour,
                "occurredAt",LocalDateTime.now().toString()
        ));
    }

    public void publishCardBlocked(String cardId, String userId,
                                   String accountId, String reason) {
        publish(cardBlockedTopic, cardId, Map.of(
                "eventId",   UUID.randomUUID().toString(),
                "eventType", "CARD_BLOCKED",
                "cardId",    cardId,
                "userId",    userId != null ? userId : "",
                "accountId", accountId,
                "reason",    reason != null ? reason : "",
                "occurredAt",LocalDateTime.now().toString()
        ));
    }

    private void publish(String topic, String key,
                         Map<String, Object> payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to {}: {}",
                                topic, ex.getMessage());
                    } else {
                        log.debug("Published to {} offset: {}",
                                topic, result.getRecordMetadata().offset());
                    }
                });
    }
}