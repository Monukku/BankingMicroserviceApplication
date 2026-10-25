package com.rewabank.payments.dto;

import com.rewabank.payments.entity.PaymentOrder;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentOrderRequest(

        @NotNull(message = "Source account ID is required")
        UUID sourceAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Minimum payment amount is ₹1")
        BigDecimal amount,

        @NotNull(message = "Payment method is required")
        PaymentOrder.PaymentMethod paymentMethod
) {}