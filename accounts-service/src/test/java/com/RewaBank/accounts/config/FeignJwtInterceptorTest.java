package com.rewabank.accounts.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FeignJwtInterceptorTest {

    private static final Instant ISSUED_AT  = Instant.ofEpochSecond(1_700_000_000L);
    private static final Instant EXPIRES_AT = Instant.ofEpochSecond(1_700_000_300L);

    private final FeignJwtInterceptor interceptor = new FeignJwtInterceptor();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void apply_ShouldInjectAuthorizationHeader_WhenJwtPresent() {
        Jwt jwt = Jwt.withTokenValue("test-token-value")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .issuedAt(ISSUED_AT)
                .expiresAt(EXPIRES_AT)
                .build();

        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).containsKey("Authorization");
        assertThat(template.headers().get("Authorization"))
                .contains("Bearer test-token-value");
    }

    @Test
    void apply_ShouldNotInjectHeader_WhenNoAuthentication() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void apply_ShouldNotInjectHeader_WhenAuthenticationIsNotJwt() {
        SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "user", "pass", List.of()));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }
}