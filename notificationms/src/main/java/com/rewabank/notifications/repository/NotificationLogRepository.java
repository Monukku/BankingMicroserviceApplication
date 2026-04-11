package com.rewabank.notifications.repository;

import com.rewabank.notifications.document.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository
        extends MongoRepository<NotificationLog, String> {

    Page<NotificationLog> findByKeycloakUserIdOrderByCreatedAtDesc(
            String keycloakUserId, Pageable pageable);

    boolean existsByEventId(String eventId);
}
