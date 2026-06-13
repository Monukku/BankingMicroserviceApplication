package com.rewabank.loans.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health",
                                "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**",
                                "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/loans",
                                "/api/v1/loans/*")
                        .hasAnyRole("CUSTOMER", "TELLER",
                                "BRANCH_MANAGER", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/v1/loans/*/approve",
                                "/api/v1/loans/*/reject",
                                "/api/v1/loans/*/disburse")
                        .hasAnyRole("CREDIT_OFFICER",
                                "BRANCH_MANAGER", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/loans/**")
                        .hasAnyRole("CUSTOMER", "TELLER",
                                "CREDIT_OFFICER", "RELATIONSHIP_MANAGER",
                                "BRANCH_MANAGER", "AUDITOR", "SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                jwtAuthenticationConverter())))
                .csrf(csrf -> csrf.disable()); // NOSONAR — stateless JWT API; CSRF only applies to cookie-based sessions
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