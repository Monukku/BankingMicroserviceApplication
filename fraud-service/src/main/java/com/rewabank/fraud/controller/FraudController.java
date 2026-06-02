package com.rewabank.fraud.controller;

import com.rewabank.fraud.model.FraudAlert;
import com.rewabank.fraud.service.FraudAlertService;
import com.rewabank.fraud.service.FraudScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
@Tag(name = "Fraud", description = "Fraud scoring and alert endpoints")
public class FraudController {

    private final FraudScoringService fraudScoringService;
    private final FraudAlertService   fraudAlertService;

    /**
     * Inline fraud score — called by Transactions MS sync.
     * Must respond in <100ms.
     * Istio enforces 3s timeout on this endpoint.
     */
    @GetMapping("/score")
    @Operation(summary = "Get fraud score for a transaction")
    public ResponseEntity<Map<String, Object>> getScore(
            @RequestParam UUID accountId,
            @RequestParam BigDecimal amount,
            @RequestParam String transactionType,
            @RequestParam String correlationId) {

        FraudScoringService.ScoringResult result =
                fraudScoringService.score(accountId, amount,
                        transactionType, correlationId);

        return ResponseEntity.ok(Map.of(
                "accountId",      accountId.toString(),
                "score",          result.score(),
                "action",         result.action(),
                "reason",         result.triggeredRules(),
                "correlationId",  correlationId
        ));
    }

    // Get open alerts — Branch Manager only
    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Get open fraud alerts")
    public ResponseEntity<Page<FraudAlert>> getOpenAlerts(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                fraudAlertService.getOpenAlerts(pageable));
    }

    // Get alerts by account
    @GetMapping("/alerts/account/{accountId}")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Get fraud alerts for an account")
    public ResponseEntity<Page<FraudAlert>> getByAccount(
            @PathVariable UUID accountId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                fraudAlertService.getByAccount(accountId, pageable));
    }

    // Resolve alert
    @PatchMapping("/alerts/{alertId}/resolve")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Resolve a fraud alert")
    public ResponseEntity<FraudAlert> resolveAlert(
            @PathVariable UUID alertId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                fraudAlertService.resolveAlert(
                        alertId,
                        jwt.getSubject(),
                        body.get("notes"),
                        body.getOrDefault("resolution", "RESOLVED")
                )
        );
    }
}
