package com.rewabank.auth.controller;

import com.rewabank.auth.dto.*;
import com.rewabank.auth.entity.BankingUser;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
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
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final Keycloak              keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    static final List<String> STAFF_ROLES = List.of(
        "TELLER", "RELATIONSHIP_MANAGER", "CREDIT_OFFICER",
        "BRANCH_MANAGER", "AUDITOR", "SUPER_ADMIN"
    );

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

        // Read first/last name from Keycloak so edits via PATCH /me are reflected immediately
        UserRepresentation kcUser = keycloak.realm(realm).users().get(keycloakUserId).toRepresentation();
        String firstName = kcUser != null && kcUser.getFirstName() != null ? kcUser.getFirstName() : "";
        String lastName  = kcUser != null && kcUser.getLastName()  != null ? kcUser.getLastName()  : "";
        String fullName  = (firstName + " " + lastName).trim();
        if (fullName.isEmpty()) fullName = user.getFullName();

        Map<String, Object> response = new HashMap<>();
        response.put("userId", user.getId());
        response.put("email", user.getEmail());
        response.put("fullName", fullName);
        response.put("firstName", firstName);
        response.put("lastName", lastName);
        response.put("maskedMobile", maskMobile(user.getMobileNumber()));
        response.put("kycVerified", user.getKycVerified());
        response.put("status", user.getStatus());

        return ResponseEntity.ok(response);
    }

    // ── Profile: Update current user name ────────────────────────────────────
    @PatchMapping("/me")
    @Operation(summary = "Update current user's first and last name")
    public ResponseEntity<Map<String, Object>> updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> body) {

        String keycloakUserId = jwt.getSubject();
        String firstName = body.get("firstName");
        String lastName  = body.get("lastName");

        if (firstName == null && lastName == null)
            throw new AuthException("AUTH_004", "At least one of firstName or lastName is required");

        var userRes = keycloak.realm(realm).users().get(keycloakUserId);
        UserRepresentation kcUser = userRes.toRepresentation();
        if (firstName != null && !firstName.isBlank()) kcUser.setFirstName(firstName.trim());
        if (lastName  != null && !lastName.isBlank())  kcUser.setLastName(lastName.trim());
        userRes.update(kcUser);

        // Sync updated fullName back to local banking_users table
        String updatedFirst = kcUser.getFirstName() != null ? kcUser.getFirstName() : "";
        String updatedLast  = kcUser.getLastName()  != null ? kcUser.getLastName()  : "";
        String updatedFull  = (updatedFirst + " " + updatedLast).trim();
        bankingUserRepository.findByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
            .ifPresent(u -> { u.setFullName(updatedFull); bankingUserRepository.save(u); });

        log.info("User {} updated name: firstName={}, lastName={}", keycloakUserId, updatedFirst, updatedLast);
        return ResponseEntity.ok(Map.of(
            "firstName", updatedFirst,
            "lastName",  updatedLast,
            "message",   "Profile updated successfully"
        ));
    }

    // ── Staff Management (SUPER_ADMIN only) ──────────────────────────────────

    @GetMapping("/staff")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all staff users with their roles")
    public ResponseEntity<List<Map<String, Object>>> listStaff() {
        // keycloakUserId -> merged entry
        java.util.LinkedHashMap<String, Map<String, Object>> byId = new java.util.LinkedHashMap<>();

        for (String role : STAFF_ROLES) {
            try {
                List<UserRepresentation> members =
                    keycloak.realm(realm).roles().get(role).getUserMembers(0, 200);
                for (UserRepresentation u : members) {
                    String uid = u.getId();
                    if (!byId.containsKey(uid)) {
                        Map<String, Object> entry = new java.util.LinkedHashMap<>();
                        entry.put("keycloakUserId", uid);
                        entry.put("email",    u.getEmail() != null ? u.getEmail() : "");
                        entry.put("fullName", fullName(u));
                        entry.put("enabled",  u.isEnabled());
                        entry.put("roles",    new java.util.ArrayList<>(List.of(role)));
                        byId.put(uid, entry);
                    } else {
                        @SuppressWarnings("unchecked")
                        List<String> roles = (List<String>) byId.get(uid).get("roles");
                        roles.add(role);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not fetch members for role {}: {}", role, e.getMessage());
            }
        }
        return ResponseEntity.ok(new java.util.ArrayList<>(byId.values()));
    }

    @PostMapping("/staff")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    @Operation(summary = "Create a new staff account")
    public ResponseEntity<Map<String, Object>> createStaff(@RequestBody Map<String, String> body) {
        String email    = body.get("email");
        String fullName = body.get("fullName");
        String role     = body.get("role");
        String password = body.getOrDefault("password", "RewaBank@2024!");

        if (email == null || fullName == null || role == null)
            throw new AuthException("AUTH_004", "email, fullName and role are required");
        if (!STAFF_ROLES.contains(role))
            throw new AuthException("AUTH_004", "Invalid staff role: " + role);

        var req = new RegisterRequest(fullName, email, "+91-0000000000", password);
        // Create disabled first — prevents the account being usable in the window between
        // creation and role assignment.
        String keycloakId = keycloakUserService.createUser(req, false);
        try {
            keycloakUserService.assignRole(keycloakId, role);
            keycloakUserService.enableUser(keycloakId);
        } catch (Exception e) {
            log.error("Staff setup failed for {} — rolling back Keycloak user: {}", email, e.getMessage());
            try { keycloakUserService.deleteUser(keycloakId); } catch (Exception ex) {
                log.error("CRITICAL: rollback failed for {} — manual cleanup required", keycloakId);
            }
            throw new AuthException("AUTH_002", "Staff account creation failed: " + e.getMessage());
        }

        log.info("Staff account created: {} with role {}", email, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "keycloakUserId", keycloakId,
            "email", email,
            "fullName", fullName,
            "role", role,
            "message", "Staff account created successfully"
        ));
    }

    @PatchMapping("/staff/{keycloakUserId}/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Assign a role to a staff member (replaces previous staff role)")
    public ResponseEntity<Map<String, Object>> assignStaffRole(
            @PathVariable String keycloakUserId,
            @RequestBody Map<String, String> body) {
        String newRole = body.get("role");
        if (newRole == null || !STAFF_ROLES.contains(newRole))
            throw new AuthException("AUTH_004", "Invalid role: " + newRole);

        // Remove existing staff roles first
        var userRolesResource = keycloak.realm(realm).users().get(keycloakUserId).roles().realmLevel();
        List<RoleRepresentation> current = userRolesResource.listAll();
        List<RoleRepresentation> toRemove = current.stream()
            .filter(r -> STAFF_ROLES.contains(r.getName()))
            .collect(Collectors.toList());
        if (!toRemove.isEmpty()) userRolesResource.remove(toRemove);

        keycloakUserService.assignRole(keycloakUserId, newRole);
        log.info("Staff role changed to {} for user {}", newRole, keycloakUserId);
        return ResponseEntity.ok(Map.of("keycloakUserId", keycloakUserId, "role", newRole));
    }

    @PatchMapping("/staff/{keycloakUserId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Enable or disable a staff account")
    public ResponseEntity<Map<String, Object>> setStaffStatus(
            @PathVariable String keycloakUserId,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        if (enabled) {
            var userRes = keycloak.realm(realm).users().get(keycloakUserId).toRepresentation();
            userRes.setEnabled(true);
            keycloak.realm(realm).users().get(keycloakUserId).update(userRes);
        } else {
            keycloakUserService.lockUser(keycloakUserId);
        }
        log.info("Staff {} {}", keycloakUserId, enabled ? "enabled" : "disabled");
        return ResponseEntity.ok(Map.of("keycloakUserId", keycloakUserId, "enabled", enabled));
    }

    private String fullName(UserRepresentation u) {
        String fn = u.getFirstName() != null ? u.getFirstName() : "";
        String ln = u.getLastName()  != null ? u.getLastName()  : "";
        if (!fn.isEmpty() && !ln.isEmpty()) return fn + " " + ln;
        if (u.getAttributes() != null) {
            var attr = u.getAttributes().get("fullName");
            if (attr != null && !attr.isEmpty()) return attr.get(0);
        }
        return fn + ln;
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 10) return "XXXXXXXXXX";
        return mobile.substring(0, 3) + "XXXXXXX" + mobile.substring(mobile.length() - 3);
    }
}
