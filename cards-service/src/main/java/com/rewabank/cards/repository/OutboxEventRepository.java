package com.rewabank.cards.repository;

import com.rewabank.cards.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc();
    List<OutboxEvent> findByEventType(String eventType);
}

