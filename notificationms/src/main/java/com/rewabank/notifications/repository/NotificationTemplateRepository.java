package com.rewabank.notifications.repository;

import com.rewabank.notifications.document.NotificationTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationTemplateRepository
        extends MongoRepository<NotificationTemplate, String> {

    Optional<NotificationTemplate> findByEventType(String eventType);
}
