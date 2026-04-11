package com.rewabank.customers.repository;

import com.rewabank.customers.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByKeycloakUserIdAndDeletedAtIsNull(String keycloakUserId);

    Optional<Customer> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByKeycloakUserIdAndDeletedAtIsNull(String keycloakUserId);

    @Query("SELECT c.kycStatus FROM Customer c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Customer.KycStatus> findKycStatusById(UUID id);

    @Query("SELECT c.kycStatus FROM Customer c WHERE c.keycloakUserId = :keycloakUserId AND c.deletedAt IS NULL")
    Optional<Customer.KycStatus> findKycStatusByKeycloakUserId(String keycloakUserId);
}
