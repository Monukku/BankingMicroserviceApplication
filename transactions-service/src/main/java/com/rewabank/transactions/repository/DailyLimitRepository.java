package com.rewabank.transactions.repository;

import com.rewabank.transactions.entity.DailyLimit;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyLimitRepository
        extends JpaRepository<DailyLimit, UUID> {

    Optional<DailyLimit> findByKeycloakUserIdAndLimitDate(
            String keycloakUserId, LocalDate limitDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<DailyLimit> findWithLockByKeycloakUserIdAndLimitDate(
            String keycloakUserId, LocalDate limitDate);
}
