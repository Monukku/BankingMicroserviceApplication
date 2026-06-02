package com.rewabank.transactions.repository;

import com.rewabank.transactions.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryRepository
        extends JpaRepository<Beneficiary, UUID> {

    List<Beneficiary> findByKeycloakUserIdAndIsActiveTrueAndDeletedAtIsNull(
            String keycloakUserId);

    Optional<Beneficiary> findByKeycloakUserIdAndBeneficiaryAccountNumberAndDeletedAtIsNull(
            String keycloakUserId, String accountNumber);
}
