package com.rewabank.accounts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ROLE_CUSTOMER             = "CUSTOMER";
    private static final String ROLE_TELLER               = "TELLER";
    private static final String ROLE_RELATIONSHIP_MANAGER = "RELATIONSHIP_MANAGER";
    private static final String ROLE_BRANCH_MANAGER       = "BRANCH_MANAGER";
    private static final String ROLE_AUDITOR              = "AUDITOR";
    private static final String ROLE_SUPER_ADMIN          = "SUPER_ADMIN";

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/actuator/health/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus"
                )
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts")
                        .hasAnyRole(ROLE_CUSTOMER, ROLE_TELLER, ROLE_BRANCH_MANAGER, ROLE_SUPER_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/accounts/**")
                        .hasAnyRole(ROLE_CUSTOMER, ROLE_TELLER, ROLE_RELATIONSHIP_MANAGER,
                                ROLE_BRANCH_MANAGER, ROLE_AUDITOR, ROLE_SUPER_ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/accounts/*/freeze",
                                "/api/v1/accounts/*/unfreeze")
                        .hasAnyRole(ROLE_BRANCH_MANAGER, ROLE_SUPER_ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/accounts/*/close")
                        .hasAnyRole(ROLE_BRANCH_MANAGER, ROLE_SUPER_ADMIN)
                        // Internal MS-to-MS endpoints — Istio AuthorizationPolicy restricts to transactions-ms-sa + loans-ms-sa
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/accounts/*/debit",
                                "/api/v1/accounts/*/credit")
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter())))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess =
                    (Map<String, Object>) jwt.getClaims().get("realm_access");
            if (realmAccess == null) return List.of();
            Collection<String> roles =
                    (Collection<String>) realmAccess.get("roles");
            return roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());
        });
        return converter;
    }
}