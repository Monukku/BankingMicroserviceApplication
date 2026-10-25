package com.rewabank.payments.dto;

import com.rewabank.payments.entity.PaymentOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentOrderResponse(
        UUID id,
        String razorpayOrderId,
        String razorpayKeyId,
        BigDecimal amount,
        String currency,
        PaymentOrder.PaymentMethod paymentMethod,
        PaymentOrder.PaymentStatus status,
        LocalDateTime createdAt
) {}