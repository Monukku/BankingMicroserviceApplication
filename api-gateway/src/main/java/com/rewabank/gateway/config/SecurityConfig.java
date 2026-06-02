package com.rewabank.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${CORS_ORIGIN_WEB:http://localhost:4200}")
    private String corsOriginWeb;

    @Value("${CORS_ORIGIN_MOBILE:http://localhost:3000}")
    private String corsOriginMobile;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .pathMatchers("/actuator/health/**", "/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .pathMatchers("/fallback/**").permitAll()

                        // â”€â”€ Role-based access â”€â”€ â† CHANGED: /rewabank/ â†’ /api/v1/
                        .pathMatchers("/api/v1/accounts/**").hasAnyRole(
                                "CUSTOMER", "TELLER", "RELATIONSHIP_MANAGER", "BRANCH_MANAGER", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/transactions/**").hasAnyRole(
                                "CUSTOMER", "TELLER", "RELATIONSHIP_MANAGER", "BRANCH_MANAGER", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/loans/**").hasAnyRole(
                                "CUSTOMER", "CREDIT_OFFICER", "BRANCH_MANAGER", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/cards/**").hasAnyRole(
                                "CUSTOMER", "TELLER", "BRANCH_MANAGER", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/customers/**").hasAnyRole(
                                "CUSTOMER", "RELATIONSHIP_MANAGER", "BRANCH_MANAGER", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/kyc/**").hasAnyRole(
                                "CUSTOMER", "RELATIONSHIP_MANAGER", "BRANCH_MANAGER", "AUDITOR", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/payments/**").hasAnyRole(
                                "CUSTOMER", "TELLER", "BRANCH_MANAGER", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/repayment/**").hasAnyRole(
                                "CUSTOMER", "TELLER", "BRANCH_MANAGER", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/statements/**").hasAnyRole(
                                "CUSTOMER", "BRANCH_MANAGER", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/reports/**").hasAnyRole(
                                "BRANCH_MANAGER", "AUDITOR", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/audit/**").hasAnyRole(
                                "AUDITOR", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/fraud/score").hasAnyRole(
                                "CUSTOMER", "TELLER", "BRANCH_MANAGER", "SUPER_ADMIN")
                        .pathMatchers("/api/v1/fraud/**").hasAnyRole(
                                "BRANCH_MANAGER", "SUPER_ADMIN")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtraction())))
                .csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return NimbusReactiveJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .webClient(WebClient.create())
                .build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtraction() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeyCloakRoleConverter()); // â† your existing class
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of(corsOriginWeb, corsOriginMobile));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Correlation-Id",
                "X-Idempotency-Key", "X-Device-Id", "X-User-Id"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}

