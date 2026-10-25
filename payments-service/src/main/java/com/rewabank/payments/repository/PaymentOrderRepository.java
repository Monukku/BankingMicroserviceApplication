package com.rewabank.payments.repository;

import com.rewabank.payments.entity.PaymentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);

    Page<PaymentOrder> findByKeycloakUserIdOrderByCreatedAtDesc(String keycloakUserId, Pageable pageable);
}