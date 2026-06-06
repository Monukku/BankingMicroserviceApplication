package com.rewabank.transactions.dto;

import com.rewabank.transactions.entity.Transaction;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(

        @NotNull(message = "Source account ID is required")
        UUID sourceAccountId,

        @NotNull(message = "Destination account ID is required")
        UUID destinationAccountId,

        @NotBlank(message = "Destination account number is required")
        @Size(min = 12, max = 12, message = "Account number must be 12 digits")
        String destinationAccountNumber,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Minimum transfer amount is ₹1")
        @DecimalMax(value = "1000000.00", message = "Maximum single transfer is ₹10,00,000")
        BigDecimal amount,

        @NotNull(message = "Transaction type is required")
        Transaction.TransactionType transactionType,

        @Size(max = 500, message = "Remarks max 500 characters")
        String remarks,

        // X-Device-Id header value — for fraud context
        @Size(max = 255, message = "Device ID max 255 characters")
        String deviceId

) {
    @AssertTrue(message = "Source and destination accounts must be different")
    public boolean isDifferentAccounts() {
        return sourceAccountId == null || destinationAccountId == null
                || !sourceAccountId.equals(destinationAccountId);
    }
}
