package com.rewabank.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void filter_setsAllSecurityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
        assertThat(responseHeaders.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(responseHeaders.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(responseHeaders.getFirst("X-XSS-Protection")).isEqualTo("1; mode=block");
        assertThat(responseHeaders.getFirst("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(responseHeaders.getFirst("Cache-Control"))
                .isEqualTo("no-store, no-cache, must-revalidate");
        assertThat(responseHeaders.getFirst("Pragma")).isEqualTo("no-cache");
        assertThat(responseHeaders.getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'self'; frame-ancestors 'none'; form-action 'self'");
        assertThat(responseHeaders.getFirst("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void filter_chainsToNextFilter() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }
}