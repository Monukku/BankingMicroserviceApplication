package com.rewabank.fraud.repository;

import com.rewabank.fraud.model.FraudAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FraudAlertRepository
        extends JpaRepository<FraudAlert, UUID> {

    List<FraudAlert> findByAccountIdAndStatus(
            UUID accountId, FraudAlert.AlertStatus status);

    Page<FraudAlert> findByStatusOrderByCreatedAtDesc(
            FraudAlert.AlertStatus status, Pageable pageable);

    Page<FraudAlert> findByAccountIdOrderByCreatedAtDesc(
            UUID accountId, Pageable pageable);
}
