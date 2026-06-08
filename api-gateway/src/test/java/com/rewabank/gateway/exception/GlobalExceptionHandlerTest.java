package com.rewabank.gateway.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handle_authenticationException_returns401() {
        MockServerWebExchange exchange = exchange("/api/v1/accounts");

        StepVerifier.create(handler.handle(exchange, new BadCredentialsException("bad creds")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handle_accessDeniedException_returns403() {
        MockServerWebExchange exchange = exchange("/api/v1/audit");

        StepVerifier.create(handler.handle(exchange, new AccessDeniedException("forbidden")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handle_tooManyRequests_returns429() {
        MockServerWebExchange exchange = exchange("/api/v1/transactions");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);

        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void handle_serviceUnavailable_returns503() {
        MockServerWebExchange exchange = exchange("/api/v1/loans");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);

        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void handle_genericException_returns500() {
        MockServerWebExchange exchange = exchange("/api/v1/cards");

        StepVerifier.create(handler.handle(exchange, new RuntimeException("unexpected")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void handle_responseBodyIsJson() {
        MockServerWebExchange exchange = exchange("/api/v1/test");

        StepVerifier.create(handler.handle(exchange, new BadCredentialsException("x")))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getContentType())
                .hasToString("application/json");
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }
}