package com.rewabank.gateway.fallback; // ← CHANGED

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    // ── Generic ───────────────────────────────────────────────────────────────
    @RequestMapping("/contactsupport")
    public Mono<ResponseEntity<Map<String, Object>>> contactSupport() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("SERVICE_UNAVAILABLE", "Service is temporarily unavailable. Please try again later.")));
    }

    // ── Per-service fallbacks ─────────────────────────────────────────────────
    @RequestMapping("/accounts")
    public Mono<ResponseEntity<Map<String, Object>>> accountsFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("ACCT_SERVICE_DOWN", "Accounts service is temporarily unavailable.")));
    }

    @RequestMapping("/transactions")
    public Mono<ResponseEntity<Map<String, Object>>> transactionsFallback() {
        // Transactions fallback — never say "try again" — user must re-initiate with new idempotency key
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("TXN_SERVICE_DOWN",
                        "Transaction service is unavailable. Do NOT retry — please check your account balance before re-initiating.")));
    }

    @RequestMapping("/payments")
    public Mono<ResponseEntity<Map<String, Object>>> paymentsFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("PAY_SERVICE_DOWN",
                        "Payment service is unavailable. Do NOT retry — check your account before re-initiating.")));
    }

    @RequestMapping("/fraud")
    public Mono<ResponseEntity<Map<String, Object>>> fraudFallback() {
        // Fraud fallback = FLAG, never approve
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("FRAUD_SERVICE_DOWN",
                        "Security check service is unavailable. Transaction cannot proceed.")));
    }

    @RequestMapping("/loans")
    public Mono<ResponseEntity<Map<String, Object>>> loansFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("LOAN_SERVICE_DOWN", "Loans service is temporarily unavailable.")));
    }

    @RequestMapping("/cards")
    public Mono<ResponseEntity<Map<String, Object>>> cardsFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("CARD_SERVICE_DOWN", "Cards service is temporarily unavailable.")));
    }

    @RequestMapping("/customers")
    public Mono<ResponseEntity<Map<String, Object>>> customersFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("CUST_SERVICE_DOWN", "Customer service is temporarily unavailable.")));
    }

    @RequestMapping("/notifications")
    public Mono<ResponseEntity<Map<String, Object>>> notificationsFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("NOTIF_SERVICE_DOWN", "Notifications service is temporarily unavailable.")));
    }

    @RequestMapping("/repayment")
    public Mono<ResponseEntity<Map<String, Object>>> repaymentFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("REPAY_SERVICE_DOWN", "Repayment service is temporarily unavailable.")));
    }

    @RequestMapping("/statements")
    public Mono<ResponseEntity<Map<String, Object>>> statementsFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("STMT_SERVICE_DOWN", "Statement service is temporarily unavailable.")));
    }

    @RequestMapping("/reports")
    public Mono<ResponseEntity<Map<String, Object>>> reportsFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("RPT_SERVICE_DOWN", "Reports service is temporarily unavailable.")));
    }

    // ── Shared fallback body builder ──────────────────────────────────────────
    private Map<String, Object> fallbackBody(String errorCode, String message) {
        return Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status",    503,
                "errorCode", errorCode,
                "message",   message
        );
    }
}
