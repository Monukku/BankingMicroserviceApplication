package com.rewabank.cards.controller;

import com.rewabank.cards.dto.*;
import com.rewabank.cards.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Card management endpoints")
public class CardController {

    private final CardService cardService;

    // ── Customer: request a new card ─────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Issue a new card")
    public ResponseEntity<CardResponse> issueCard(
            @Valid @RequestBody CardIssueRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cardService.issueCard(jwt.getSubject(), request, idempotencyKey));
    }

    // ── Customer: list own cards ──────────────────────────────────────────────
    @GetMapping("/my-cards")
    @Operation(summary = "Get my cards")
    public ResponseEntity<List<CardResponse>> getMyCards(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cardService.getMyCards(jwt.getSubject()));
    }

    // ── Customer/Staff: get card by ID ────────────────────────────────────────
    @GetMapping("/{id}")
    @Operation(summary = "Get card by ID")
    public ResponseEntity<CardResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        boolean isStaff = isStaffCaller();
        if (isStaff) {
            // Staff can view any card
            return ResponseEntity.ok(cardService.getByIdForStaff(id));
        }
        return ResponseEntity.ok(cardService.getById(id, jwt.getSubject()));
    }

    // ── Customer: block own card (OTP required — RBI mandate) ─────────────────
    // ── Staff: block any card (reason only, no OTP) ───────────────────────────
    @PatchMapping("/{id}/block")
    @Operation(summary = "Block card — OTP required for customer; reason only for staff")
    public ResponseEntity<CardResponse> blockCard(
            @PathVariable UUID id,
            @RequestBody(required = false) CardBlockRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        boolean isStaff = isStaffCaller();
        CardBlockRequest req = request != null ? request : new CardBlockRequest(null, null);
        return ResponseEntity.ok(cardService.blockCard(id, jwt.getSubject(), req, isStaff));
    }

    // ── Customer/Staff: unblock card ──────────────────────────────────────────
    @PatchMapping("/{id}/unblock")
    @Operation(summary = "Unblock card")
    public ResponseEntity<CardResponse> unblockCard(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                cardService.unblockCard(id, jwt.getSubject(), isStaffCaller()));
    }

    // ── Staff: activate PENDING card (Teller hands card to customer) ──────────
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('TELLER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Activate a pending card — Teller only")
    public ResponseEntity<CardResponse> activateCard(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cardService.activateCard(id, jwt.getSubject()));
    }

    // ── Staff: permanently cancel a card (compliance/fraud) ───────────────────
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Cancel a card permanently — Branch Manager only")
    public ResponseEntity<CardResponse> cancelCard(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(cardService.cancelCard(id, jwt.getSubject(), reason));
    }

    // ── Staff: get all cards for a customer by UUID ──────────────────────────
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('TELLER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Get all cards for a customer — staff only")
    public ResponseEntity<List<CardResponse>> getCardsByCustomer(
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(cardService.getCardsByCustomer(customerId));
    }

    // ── Staff: get all cards for a customer by keycloakUserId ────────────────
    @GetMapping("/customer/by-user/{keycloakUserId}")
    @PreAuthorize("hasAnyRole('TELLER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Get all cards for a customer by keycloakUserId — staff only")
    public ResponseEntity<List<CardResponse>> getCardsByKeycloakUserId(
            @PathVariable String keycloakUserId) {
        return ResponseEntity.ok(cardService.getCardsByKeycloakUserId(keycloakUserId));
    }

    // ── Staff: get all cards ──────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('TELLER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Get all cards — staff only")
    public ResponseEntity<List<CardResponse>> getAllCards() {
        return ResponseEntity.ok(cardService.getAllCards());
    }

    // ── Staff: get all PENDING cards (activation queue) ───────────────────────
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('TELLER','BRANCH_MANAGER','SUPER_ADMIN')")
    @Operation(summary = "Get all pending cards awaiting activation — staff only")
    public ResponseEntity<List<CardResponse>> getPendingCards() {
        return ResponseEntity.ok(cardService.getPendingCards());
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private boolean isStaffCaller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;   // unauthenticated / no security context → not staff (never NPE)
        }
        return auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return r.equals("ROLE_TELLER") || r.equals("ROLE_BRANCH_MANAGER")
                    || r.equals("ROLE_SUPER_ADMIN") || r.equals("ROLE_RELATIONSHIP_MANAGER");
        });
    }
}