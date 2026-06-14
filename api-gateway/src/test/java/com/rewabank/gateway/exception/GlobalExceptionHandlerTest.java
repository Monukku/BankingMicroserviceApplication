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

    @Test
    void handle_authenticationException_bodyContainsErrorCode() {
        MockServerWebExchange exchange = exchange("/api/v1/accounts");

        StepVerifier.create(handler.handle(exchange, new BadCredentialsException("bad creds")))
                .verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> {
                    assertThat(body).contains("GW_AUTH_001");
                    assertThat(body).contains("Authentication required");
                    assertThat(body).contains("401");
                })
                .verifyComplete();
    }

    @Test
    void handle_accessDeniedException_bodyContainsErrorCode() {
        MockServerWebExchange exchange = exchange("/api/v1/audit");

        StepVerifier.create(handler.handle(exchange, new AccessDeniedException("forbidden")))
                .verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> {
                    assertThat(body).contains("GW_AUTH_002");
                    assertThat(body).contains("Insufficient permissions");
                    assertThat(body).contains("403");
                })
                .verifyComplete();
    }

    @Test
    void handle_tooManyRequests_bodyContainsRateErrorCode() {
        MockServerWebExchange exchange = exchange("/api/v1/transactions");

        StepVerifier.create(handler.handle(exchange, new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)))
                .verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("GW_RATE_001"))
                .verifyComplete();
    }

    @Test
    void handle_serviceUnavailable_bodyContainsCbErrorCode() {
        MockServerWebExchange exchange = exchange("/api/v1/loans");

        StepVerifier.create(handler.handle(exchange, new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)))
                .verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("GW_CB_001"))
                .verifyComplete();
    }

    @Test
    void handle_genericException_bodyContainsGenericErrorCode() {
        MockServerWebExchange exchange = exchange("/api/v1/cards");

        StepVerifier.create(handler.handle(exchange, new RuntimeException("unexpected")))
                .verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("GW_ERR_001"))
                .verifyComplete();
    }

    @Test
    void handle_withCorrelationIdHeader_includesItInBody() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test")
                        .header("rewabank-correlation-id", "corr-test-999")
                        .build());

        StepVerifier.create(handler.handle(exchange, new BadCredentialsException("x")))
                .verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("corr-test-999"))
                .verifyComplete();
    }

    @Test
    void handle_withoutCorrelationIdHeader_usesUnknownFallback() {
        // Kills NegateConditionals on the != null ternary guard
        MockServerWebExchange exchange = exchange("/api/v1/test");

        StepVerifier.create(handler.handle(exchange, new BadCredentialsException("x")))
                .verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> assertThat(body).contains("unknown"))
                .verifyComplete();
    }

    @Test
    void handle_bodyContainsPathAndTimestamp() {
        MockServerWebExchange exchange = exchange("/api/v1/accounts");

        StepVerifier.create(handler.handle(exchange, new BadCredentialsException("x")))
                .verifyComplete();

        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body -> {
                    assertThat(body).contains("/api/v1/accounts");
                    assertThat(body).contains("timestamp");
                })
                .verifyComplete();
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }
}