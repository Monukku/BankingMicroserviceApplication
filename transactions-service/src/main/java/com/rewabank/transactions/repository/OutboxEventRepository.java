package com.rewabank.transactions.repository;

import com.rewabank.transactions.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.status = 'PENDING'
        AND o.retryCount < 5
        AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now)
        ORDER BY o.createdAt ASC
        LIMIT 50
        """)
    List<OutboxEvent> findPendingEvents(@Param("now") LocalDateTime now);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = 'PUBLISHED' AND o.processedAt < :before")
    int deletePublished(@Param("before") LocalDateTime before);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.createdAt < :before")
    int deleteFailed(@Param("before") LocalDateTime before);
}
