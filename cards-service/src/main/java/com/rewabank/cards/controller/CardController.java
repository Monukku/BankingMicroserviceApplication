package com.rewabank.cards.controller;

import com.rewabank.cards.dto.*;
import com.rewabank.cards.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Card management endpoints")
public class CardController {

    private final CardService cardService;

    @PostMapping
    @Operation(summary = "Issue a new card")
    public ResponseEntity<CardResponse> issueCard(
            @Valid @RequestBody CardIssueRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cardService.issueCard(jwt.getSubject(), request, idempotencyKey));
    }

    @GetMapping("/my-cards")
    @Operation(summary = "Get my cards")
    public ResponseEntity<List<CardResponse>> getMyCards(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                cardService.getMyCards(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get card by ID")
    public ResponseEntity<CardResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                cardService.getById(id, jwt.getSubject()));
    }

    /**
     * Block card — OTP required (RBI mandate).
     * OTP must be verified before calling this endpoint.
     * In production: verify OTP against Auth MS first.
     */
    @PatchMapping("/{id}/block")
    @Operation(summary = "Block card — OTP required")
    public ResponseEntity<CardResponse> blockCard(
            @PathVariable UUID id,
            @Valid @RequestBody CardBlockRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                cardService.blockCard(id, jwt.getSubject(), request));
    }

    @PatchMapping("/{id}/unblock")
    @Operation(summary = "Unblock card — OTP required")
    public ResponseEntity<CardResponse> unblockCard(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(
                cardService.unblockCard(id, jwt.getSubject()));
    }
}