package com.rewabank.accounts.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.accounts.entity.OutboxEvent;
import com.rewabank.accounts.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventSaver {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper          objectMapper;

    @Transactional
    public void save(String aggregateId, String eventType,
                     String topic, Map<String, Object> payload) {
        try {
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();
            outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload for {}: {}",
                    eventType, e.getMessage());
            throw new IllegalStateException("Outbox serialization failed", e);
        }
    }
}