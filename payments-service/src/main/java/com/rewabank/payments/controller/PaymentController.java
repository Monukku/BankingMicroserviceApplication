package com.rewabank.payments.controller;

import com.rewabank.payments.dto.PaymentOrderRequest;
import com.rewabank.payments.dto.PaymentOrderResponse;
import com.rewabank.payments.service.PaymentOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Razorpay payment gateway endpoints")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    @PostMapping("/order")
    @Operation(summary = "Create a Razorpay payment order")
    public ResponseEntity<PaymentOrderResponse> createOrder(
            @Valid @RequestBody PaymentOrderRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentOrderService.createOrder(
                        jwt.getSubject(), idempotencyKey, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment order by ID")
    public ResponseEntity<PaymentOrderResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                paymentOrderService.getById(id, jwt.getSubject()));
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Get my payment orders (paginated)")
    public ResponseEntity<Page<PaymentOrderResponse>> getMyOrders(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable) {
        return ResponseEntity.ok(
                paymentOrderService.getMyOrders(jwt.getSubject(), pageable));
    }
}