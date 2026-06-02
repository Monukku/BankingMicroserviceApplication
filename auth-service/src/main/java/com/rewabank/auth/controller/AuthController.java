package com.rewabank.auth.controller;

import com.rewabank.auth.dto.*;
import com.rewabank.auth.entity.BankingUser;
import java.util.HashMap;
import com.rewabank.auth.exception.AuthException;
import com.rewabank.auth.kafka.AuthEventProducer;
import com.rewabank.auth.repository.BankingUserRepository;
import com.rewabank.auth.service.KeycloakUserService;
import com.rewabank.auth.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "Authentication and OTP endpoints")
public class AuthController {

    private final KeycloakUserService   keycloakUserService;
    private final OtpService            otpService;
    private final BankingUserRepository bankingUserRepository;
    private final AuthEventProducer     authEventProducer;

    // ── Registration ──────────────────────────────────────────────────────────
    @PostMapping("/register")
    @Transactional
    @Operation(summary = "Register a new banking user")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        // Duplicate check before calling Keycloak
        if (bankingUserRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new AuthException("AUTH_001", "User already exists with this email");
        }
        if (bankingUserRepository.existsByMobileNumberAndDeletedAtIsNull(request.mobileNumber())) {
            throw new AuthException("AUTH_001", "User already exists with this mobile number");
        }

        // Create user in Keycloak — sync call, must succeed before we save locally
        String keycloakUserId = keycloakUserService.createUser(request);

        // Save banking profile locally
        BankingUser user = BankingUser.builder()
                .keycloakUserId(keycloakUserId)
                .email(request.email())
                .mobileNumber(request.mobileNumber())
                .fullName(request.fullName())
                .build();
        bankingUserRepository.save(user);

        // Async — publish to Kafka, Customers MS will create KYC profile
        authEventProducer.publishUserRegistered(
                keycloakUserId,
                request.email(),
                request.mobileNumber(),
                request.fullName()
        );

        log.info("User registered successfully: {}", request.email());

        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(
                keycloakUserId,
                request.email(),
                maskMobile(request.mobileNumber()),
                "Registration successful. Please complete KYC to activate your account.",
                LocalDateTime.now()
        ));
    }

    // ── OTP: Generate (banking operations — NOT login) ─────────────────────
    @PostMapping("/otp/generate")
    @Operation(summary = "Generate banking OTP for transfer/card operations")
    public ResponseEntity<OtpResponse> generateOtp(
            @Valid @RequestBody OtpRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakUserId = jwt.getSubject();
        OtpResponse response  = otpService.generateOtp(keycloakUserId, request);
        return ResponseEntity.ok(response);
    }

    // ── OTP: Verify ──────────────────────────────────────────────────────────
    @PostMapping("/otp/verify")
    @Operation(summary = "Verify banking OTP")
    public ResponseEntity<OtpResponse> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String keycloakUserId = jwt.getSubject();
        boolean verified = otpService.verifyOtp(
                keycloakUserId, request.otp(), request.purpose());

        return ResponseEntity.ok(new OtpResponse(
                verified,
                verified ? "OTP verified successfully" : "Invalid OTP",
                null,
                null
        ));
    }

    // ── Profile: Current user ────────────────────────────────────────────────
    @GetMapping("/me")
    @Operation(summary = "Get current user banking profile")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {

        String keycloakUserId = jwt.getSubject();

        BankingUser user = bankingUserRepository
                .findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .orElseThrow(() -> new AuthException("AUTH_003", "User not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("userId", user.getId());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName());
        response.put("maskedMobile", maskMobile(user.getMobileNumber()));
        response.put("kycVerified", user.getKycVerified());
        response.put("status", user.getStatus());

        return ResponseEntity.ok(response);
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 10) return "XXXXXXXXXX";
        return mobile.substring(0, 3) + "XXXXXXX" + mobile.substring(mobile.length() - 3);
    }
}
