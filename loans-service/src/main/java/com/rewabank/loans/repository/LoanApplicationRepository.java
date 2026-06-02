package com.rewabank.loans.repository;

import com.rewabank.loans.entity.LoanApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanApplicationRepository
        extends JpaRepository<LoanApplication, UUID> {

    List<LoanApplication> findByKeycloakUserIdOrderByCreatedAtDesc(
            String keycloakUserId);

    Page<LoanApplication> findByStatusOrderByCreatedAtDesc(
            LoanApplication.LoanStatus status, Pageable pageable);

    Page<LoanApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);
}