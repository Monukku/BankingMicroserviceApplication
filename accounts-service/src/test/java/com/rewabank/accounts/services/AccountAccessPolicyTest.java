package com.rewabank.accounts.services;

import com.rewabank.accounts.exception.AccountException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the IDOR read-access policy — focuses on the privilege check's
 * defensive handling of missing/malformed JWT claims and the owner/privileged/
 * denied decision matrix.
 */
class AccountAccessPolicyTest {

    private final AccountAccessPolicy policy = new AccountAccessPolicy();

    private static Jwt jwt(String subject, Object realmAccess) {
        Jwt.Builder b = Jwt.withTokenValue("token").header("alg", "none").subject(subject);
        if (realmAccess != null) {
            b.claim("realm_access", realmAccess);
        }
        return b.build();
    }

    // ── isPrivileged — malformed/missing claims must be treated as NOT privileged ──

    @Test
    void isPrivileged_False_WhenJwtNull() {
        assertFalse(policy.isPrivileged(null));
    }

    @Test
    void isPrivileged_False_WhenRealmAccessClaimMissing() {
        assertFalse(policy.isPrivileged(jwt("u1", null)));
    }

    @Test
    void isPrivileged_False_WhenRealmAccessNotAMap() {
        Jwt j = jwt("u1", "not-a-map");
        assertFalse(policy.isPrivileged(j));
    }

    @Test
    void isPrivileged_False_WhenRolesNotACollection() {
        assertFalse(policy.isPrivileged(jwt("u1", Map.of("roles", "ADMIN"))));
    }

    @Test
    void isPrivileged_False_WhenRolesHoldNoPrivilegedRole() {
        assertFalse(policy.isPrivileged(jwt("u1", Map.of("roles", List.of("CUSTOMER")))));
    }

    @Test
    void isPrivileged_True_WhenRolesHoldStaffRole() {
        assertTrue(policy.isPrivileged(jwt("u1", Map.of("roles", List.of("CUSTOMER", "BRANCH_MANAGER")))));
    }

    @Test
    void isPrivileged_True_WhenRolesHoldInternalServiceRole() {
        assertTrue(policy.isPrivileged(jwt("svc", Map.of("roles", List.of("INTERNAL_SERVICE")))));
    }

    // ── requireReadAccess — owner / privileged allowed, everyone else 404 (no leak) ──

    @Test
    void requireReadAccess_Allows_Owner() {
        assertDoesNotThrow(() ->
                policy.requireReadAccess("owner-1", jwt("owner-1", Map.of("roles", List.of("CUSTOMER")))));
    }

    @Test
    void requireReadAccess_Allows_PrivilegedNonOwner() {
        assertDoesNotThrow(() ->
                policy.requireReadAccess("owner-1", jwt("staff-9", Map.of("roles", List.of("AUDITOR")))));
    }

    @Test
    void requireReadAccess_Throws404_ForNonOwnerNonPrivileged() {
        AccountException ex = assertThrows(AccountException.class, () ->
                policy.requireReadAccess("owner-1", jwt("attacker", Map.of("roles", List.of("CUSTOMER")))));
        assertEquals("ACCT_002", ex.getErrorCode());
    }

    @Test
    void requireReadAccess_Throws404_WhenJwtNull() {
        AccountException ex = assertThrows(AccountException.class, () ->
                policy.requireReadAccess("owner-1", null));
        assertEquals("ACCT_002", ex.getErrorCode());
    }
}