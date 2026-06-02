package com.rewabank.transactions.repository;

import com.rewabank.transactions.entity.DailyLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyLimitRepository
        extends JpaRepository<DailyLimit, UUID> {

    Optional<DailyLimit> findByKeycloakUserIdAndLimitDate(
            String keycloakUserId, LocalDate limitDate);
}
