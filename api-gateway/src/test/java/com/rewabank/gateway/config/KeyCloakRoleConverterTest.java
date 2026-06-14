package com.rewabank.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeyCloakRoleConverterTest {

    private final KeyCloakRoleConverter converter = new KeyCloakRoleConverter();

    private Jwt jwtWithRoles(List<String> roles) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaims()).thenReturn(Map.of(
                "realm_access", Map.of("roles", roles)
        ));
        return jwt;
    }

    private Jwt jwtWithClaims(Map<String, Object> claims) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaims()).thenReturn(claims);
        return jwt;
    }

    @Test
    void convert_singleRole_prefixesWithRole() {
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithRoles(List.of("CUSTOMER")));
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void convert_multipleRoles_returnsAllPrefixed() {
        Collection<GrantedAuthority> authorities = converter.convert(
                jwtWithRoles(List.of("CUSTOMER", "RELATIONSHIP_MANAGER")));
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_RELATIONSHIP_MANAGER");
    }

    @Test
    void convert_noRealmAccess_returnsEmptyList() {
        // Kills NullReturn + NegateConditionals on: if (realmAccess == null || ...)
        Collection<GrantedAuthority> authorities = converter.convert(
                jwtWithClaims(Map.of()));
        assertThat(authorities).isEmpty();
    }

    @Test
    void convert_emptyRoles_returnsEmptyList() {
        // Kills NegateConditionals on: realmAccess.isEmpty()
        Collection<GrantedAuthority> authorities = converter.convert(
                jwtWithClaims(Map.of("realm_access", Map.of())));
        assertThat(authorities).isEmpty();
    }

    @Test
    void convert_rolesListIsEmpty_returnsEmptyList() {
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithRoles(List.of()));
        assertThat(authorities).isEmpty();
    }
}