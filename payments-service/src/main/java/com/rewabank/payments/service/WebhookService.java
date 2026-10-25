package com.rewabank.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewabank.payments.config.RazorpayConfig;
import com.rewabank.payments.entity.PaymentOrder;
import com.rewabank.payments.exception.PaymentException;
import com.rewabank.payments.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final OutboxPublisherService outboxPublisherService;
    private final RazorpayConfig         razorpayConfig;
    private final ObjectMapper           objectMapper;

    private static final String TOPIC_PAYMENT_COMPLETED = "bank.payment.completed";
    private static final String TOPIC_PAYMENT_FAILED    = "bank.payment.failed";

    @Transactional
    public void handleWebhook(String payload, String signature) {
        verifySignature(payload, signature);

        try {
            JsonNode root          = objectMapper.readTree(payload);
            String   event         = root.path("event").asText();
            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");

            String razorpayOrderId   = paymentEntity.path("order_id").asText();
            String razorpayPaymentId = paymentEntity.path("id").asText();

            PaymentOrder order = paymentOrderRepository
                    .findByRazorpayOrderId(razorpayOrderId)
                    .orElseThrow(() -> new PaymentException("PYMT_004",
                            "Payment order not found for orderId: " + razorpayOrderId));

            switch (event) {
                case "payment.captured" -> handleSuccess(order, razorpayPaymentId);
                case "payment.failed"   -> handleFailure(order,
                        paymentEntity.path("error_description").asText("Payment failed"));
                default -> log.debug("Unhandled Razorpay event: {}", event);
            }

        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage(), e);
            throw new PaymentException("PYMT_005", "Webhook processing failed");
        }
    }

    private void handleSuccess(PaymentOrder order, String razorpayPaymentId) {
        if (order.getStatus() == PaymentOrder.PaymentStatus.COMPLETED) {
            log.info("Duplicate webhook — payment already completed: {}", order.getId());
            return;
        }

        order.setStatus(PaymentOrder.PaymentStatus.COMPLETED);
        order.setRazorpayPaymentId(razorpayPaymentId);
        order.setCompletedAt(LocalDateTime.now());
        paymentOrderRepository.save(order);

        outboxPublisherService.save(
                order.getId().toString(),
                "PAYMENT_COMPLETED",
                TOPIC_PAYMENT_COMPLETED,
                Map.of(
                        "paymentOrderId",      order.getId().toString(),
                        "keycloakUserId",      order.getKeycloakUserId(),
                        "sourceAccountId",     order.getSourceAccountId().toString(),
                        "sourceAccountNumber", order.getSourceAccountNumber(),
                        "amount",              order.getAmount().toString(),
                        "currency",            order.getCurrency(),
                        "paymentMethod",       order.getPaymentMethod().name(),
                        "razorpayPaymentId",   razorpayPaymentId,
                        "completedAt",         order.getCompletedAt().toString()
                )
        );

        log.info("Payment completed: {} razorpayPaymentId: {}", order.getId(), razorpayPaymentId);
    }

    private void handleFailure(PaymentOrder order, String reason) {
        order.setStatus(PaymentOrder.PaymentStatus.FAILED);
        order.setFailureReason(reason);
        paymentOrderRepository.save(order);

        outboxPublisherService.save(
                order.getId().toString(),
                "PAYMENT_FAILED",
                TOPIC_PAYMENT_FAILED,
                Map.of(
                        "paymentOrderId", order.getId().toString(),
                        "keycloakUserId", order.getKeycloakUserId(),
                        "amount",         order.getAmount().toString(),
                        "reason",         reason
                )
        );

        log.warn("Payment failed: {} reason: {}", order.getId(), reason);
    }

    // HMAC-SHA256 signature verification — Razorpay webhook security
    private void verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    razorpayConfig.getWebhookSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));

            if (!sb.toString().equals(signature)) {
                log.warn("Invalid Razorpay webhook signature");
                throw new PaymentException("PYMT_006", "Invalid webhook signature");
            }
        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentException("PYMT_006", "Signature verification failed");
        }
    }
}