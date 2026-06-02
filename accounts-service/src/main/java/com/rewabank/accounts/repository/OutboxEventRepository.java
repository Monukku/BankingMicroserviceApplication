package com.rewabank.accounts.repository;

import com.rewabank.accounts.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // Fetch pending events ordered by creation — max 50 per batch
    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.status = 'PENDING'
        AND o.retryCount < 5
        ORDER BY o.createdAt ASC
        LIMIT 50
        """)
    List<OutboxEvent> findPendingEvents();
}
