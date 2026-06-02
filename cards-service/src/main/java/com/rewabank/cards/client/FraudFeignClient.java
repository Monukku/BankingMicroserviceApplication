package com.rewabank.cards.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "fraud-ms", url = "${feign.fraud-ms.url:http://localhost:8095}")
public interface FraudFeignClient {

    /**
     * Validate transaction against fraud rules
     * @return Map with fraudScore, approved flag, and reason if declined
     */
    @PostMapping("/api/v1/fraud/validate-transaction")
    Map<String, Object> validateTransaction(@RequestBody TransactionValidationRequest request);

    /**
     * Report suspicious transaction
     */
    @PostMapping("/api/v1/fraud/report")
    Map<String, Object> reportSuspiciousTransaction(@RequestBody FraudReportRequest request);

    /**
     * Transaction validation request DTO
     */
    class TransactionValidationRequest {
        public UUID cardId;
        public UUID customerId;
        public BigDecimal amount;
        public String currency;
        public String merchantId;
        public String transactionType;
        public Boolean isInternational;
        public String channel;

        public TransactionValidationRequest(UUID cardId, UUID customerId, BigDecimal amount,
                String currency, String merchantId, String transactionType,
                Boolean isInternational, String channel) {
            this.cardId = cardId;
            this.customerId = customerId;
            this.amount = amount;
            this.currency = currency;
            this.merchantId = merchantId;
            this.transactionType = transactionType;
            this.isInternational = isInternational;
            this.channel = channel;
        }
    }

    /**
     * Fraud report request DTO
     */
    class FraudReportRequest {
        public UUID transactionId;
        public UUID cardId;
        public String fraudType;
        public String details;

        public FraudReportRequest(UUID transactionId, UUID cardId, String fraudType, String details) {
            this.transactionId = transactionId;
            this.cardId = cardId;
            this.fraudType = fraudType;
            this.details = details;
        }
    }
}

