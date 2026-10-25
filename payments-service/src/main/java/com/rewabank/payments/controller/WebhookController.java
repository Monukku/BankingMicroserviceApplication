package com.rewabank.payments.controller;

import com.rewabank.payments.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    // Razorpay calls this endpoint directly — no JWT, verified by HMAC signature
    @PostMapping("/razorpay")
    public ResponseEntity<Map<String, String>> handleRazorpay(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        if (signature == null || signature.isBlank()) {
            log.warn("Razorpay webhook received without signature — rejected");
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "rejected", "reason", "missing signature"));
        }

        webhookService.handleWebhook(payload, signature);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}