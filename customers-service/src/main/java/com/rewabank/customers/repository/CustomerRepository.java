package com.rewabank.customers.repository;

import com.rewabank.customers.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByKeycloakUserIdAndDeletedAtIsNull(String keycloakUserId);

    Optional<Customer> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByKeycloakUserIdAndDeletedAtIsNull(String keycloakUserId);

    // Used for registration idempotency — must include soft-deleted records so a
    // re-registration after soft-delete doesn't create a duplicate identity.
    boolean existsByKeycloakUserId(String keycloakUserId);

    @Query("SELECT c.kycStatus FROM Customer c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Customer.KycStatus> findKycStatusById(UUID id);

    @Query("SELECT c.kycStatus FROM Customer c WHERE c.keycloakUserId = :keycloakUserId AND c.deletedAt IS NULL")
    Optional<Customer.KycStatus> findKycStatusByKeycloakUserId(String keycloakUserId);

    List<Customer> findByKycStatusInAndDeletedAtIsNull(List<Customer.KycStatus> statuses);

    List<Customer> findByEmailContainingIgnoreCaseAndDeletedAtIsNull(String email);

    @Query(value = "SELECT * FROM customers WHERE deleted_at IS NULL AND LOWER(full_name) LIKE :pattern",
           nativeQuery = true)
    List<Customer> searchByName(@Param("pattern") String pattern);
}
