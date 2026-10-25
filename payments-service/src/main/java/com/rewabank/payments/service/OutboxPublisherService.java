package com.rewabank.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.payments.entity.OutboxEvent;
import com.rewabank.payments.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String aggregateId, String eventType, String topic, Map<String, Object> payload) {
        try {
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();
            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to save outbox event [{}]: {}", eventType, e.getMessage(), e);
            throw new RuntimeException("Outbox save failed", e);
        }
    }
}