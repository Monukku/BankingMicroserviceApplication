package com.rewabank.accounts.services;

import com.rewabank.accounts.exception.AccountException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Shared account read-access policy (used by get-balance and get-by-id).
 *
 * A caller may read an account only if they are the OWNER (JWT subject ==
 * account.keycloakUserId) or hold a PRIVILEGED role (staff or the service
 * identity INTERNAL_SERVICE). A non-owner, non-privileged caller is treated as
 * "not found" (ACCT_002 → 404) so the endpoint does not leak whether the
 * account exists.
 */
@Component
public class AccountAccessPolicy {

    // Raw Keycloak realm role names — the realm_access.roles claim has no ROLE_ prefix.
    private static final Set<String> PRIVILEGED_ROLES = Set.of(
            "TELLER", "RELATIONSHIP_MANAGER", "BRANCH_MANAGER",
            "AUDITOR", "SUPER_ADMIN", "INTERNAL_SERVICE");

    /**
     * @throws AccountException ACCT_002 (→404) if the caller is neither the owner
     *                          nor privileged. 404 (not 403) avoids leaking existence.
     */
    public void requireReadAccess(String ownerKeycloakId, Jwt jwt) {
        if (jwt != null && (isPrivileged(jwt) || jwt.getSubject().equals(ownerKeycloakId))) {
            return;
        }
        throw new AccountException("ACCT_002", "Account not found");
    }

    /** True if the token carries any staff role or the INTERNAL_SERVICE service role. */
    public boolean isPrivileged(Jwt jwt) {
        if (jwt == null) return false;
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmMap)) return false;
        Object roles = realmMap.get("roles");
        if (!(roles instanceof Collection<?> roleList)) return false;
        return roleList.stream().anyMatch(PRIVILEGED_ROLES::contains);
    }
}