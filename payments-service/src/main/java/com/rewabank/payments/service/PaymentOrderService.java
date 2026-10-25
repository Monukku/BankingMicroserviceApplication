package com.rewabank.payments.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.rewabank.payments.client.AccountsFeignClient;
import com.rewabank.payments.client.dto.AccountResponse;
import com.rewabank.payments.config.RazorpayConfig;
import com.rewabank.payments.dto.PaymentOrderRequest;
import com.rewabank.payments.dto.PaymentOrderResponse;
import com.rewabank.payments.entity.PaymentOrder;
import com.rewabank.payments.exception.PaymentException;
import com.rewabank.payments.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrderService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final IdempotencyService     idempotencyService;
    private final OutboxPublisherService outboxPublisherService;
    private final RazorpayClient         razorpayClient;
    private final RazorpayConfig         razorpayConfig;
    private final AccountsFeignClient    accountsFeignClient;

    private static final String TOPIC_PAYMENT_INITIATED = "bank.payment.initiated";

    @Transactional
    public PaymentOrderResponse createOrder(String keycloakUserId,
                                            String idempotencyKey,
                                            PaymentOrderRequest request) {
        // Idempotency check
        if (idempotencyService.exists(idempotencyKey)) {
            String existingId = idempotencyService.get(idempotencyKey);
            return paymentOrderRepository.findById(UUID.fromString(existingId))
                    .map(this::toResponse)
                    .orElseThrow(() -> new PaymentException("PYMT_001", "Payment order not found"));
        }

        // Verify account ownership — user cannot credit someone else's account
        AccountResponse account;
        try {
            account = accountsFeignClient.getById(request.sourceAccountId());
        } catch (Exception e) {
            log.error("Account lookup failed for accountId: {}", request.sourceAccountId());
            throw new PaymentException("PYMT_007", "Account not found or unavailable");
        }
        if (!keycloakUserId.equals(account.keycloakUserId())) {
            log.warn("Ownership check failed — userId: {} accountId: {}", keycloakUserId, request.sourceAccountId());
            throw new PaymentException("PYMT_008", "Account does not belong to the requesting user");
        }
        if (!"ACTIVE".equals(account.status())) {
            throw new PaymentException("PYMT_009", "Account is not active");
        }

        // Create Razorpay order — amount in paise (multiply by 100)
        String razorpayOrderId;
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", request.amount()
                    .multiply(new BigDecimal("100")).intValue());
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "rcpt_" + idempotencyKey);

            Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            razorpayOrderId = razorpayOrder.get("id");
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new PaymentException("PYMT_002", "Payment gateway error — please try again");
        }

        // Persist payment order
        PaymentOrder order = PaymentOrder.builder()
                .idempotencyKey(idempotencyKey)
                .keycloakUserId(keycloakUserId)
                .sourceAccountId(request.sourceAccountId())
                .sourceAccountNumber(account.accountNumber()) // from accounts-service, not client
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .razorpayOrderId(razorpayOrderId)
                .status(PaymentOrder.PaymentStatus.CREATED)
                .build();

        PaymentOrder saved = paymentOrderRepository.save(order);

        // Outbox event — same transaction
        outboxPublisherService.save(
                saved.getId().toString(),
                "PAYMENT_INITIATED",
                TOPIC_PAYMENT_INITIATED,
                Map.of(
                        "paymentOrderId",  saved.getId().toString(),
                        "keycloakUserId",  keycloakUserId,
                        "amount",          request.amount().toString(),
                        "currency",        "INR",
                        "paymentMethod",   request.paymentMethod().name(),
                        "razorpayOrderId", razorpayOrderId
                )
        );

        // Store idempotency key
        idempotencyService.store(idempotencyKey, saved.getId().toString());

        log.info("Payment order created: {} razorpayOrderId: {}", saved.getId(), razorpayOrderId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse getById(UUID id, String keycloakUserId) {
        return paymentOrderRepository.findById(id)
                .filter(o -> o.getKeycloakUserId().equals(keycloakUserId))
                .map(this::toResponse)
                .orElseThrow(() -> new PaymentException("PYMT_003", "Payment order not found"));
    }

    @Transactional(readOnly = true)
    public Page<PaymentOrderResponse> getMyOrders(String keycloakUserId, Pageable pageable) {
        return paymentOrderRepository
                .findByKeycloakUserIdOrderByCreatedAtDesc(keycloakUserId, pageable)
                .map(this::toResponse);
    }

    private PaymentOrderResponse toResponse(PaymentOrder o) {
        return new PaymentOrderResponse(
                o.getId(),
                o.getRazorpayOrderId(),
                razorpayConfig.getKeyId(),
                o.getAmount(),
                o.getCurrency(),
                o.getPaymentMethod(),
                o.getStatus(),
                o.getCreatedAt()
        );
    }
}