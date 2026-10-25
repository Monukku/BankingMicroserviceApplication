package com.rewabank.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the circuit-breaker + time-limiter config is driven entirely by application.yml
 * (resilience4j.* via the resilience4j-spring-boot3 registries), after the Java Customizer
 * beans were removed. If the YAML weren't being consumed, these registries would carry
 * resilience4j library defaults (sliding window 100, no record-exceptions) and these
 * assertions would fail — i.e. this is the guard that YAML-only actually works.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/realms/test/protocol/openid-connect/certs",
    "CORS_ORIGIN_WEB=http://localhost:4200",
    "CORS_ORIGIN_MOBILE=http://localhost:3000",
    "build.version=1.0.0",
    "rate-limiting.enabled=false"
})
class CircuitBreakerConfigTest {

    @MockitoBean ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired TimeLimiterRegistry    timeLimiterRegistry;

    @Value("${spring.cloud.gateway.httpclient.response-timeout}")
    Duration httpClientResponseTimeout;

    @Test
    void httpClientBackstop_isAtLeastEveryPerRouteTimeLimiter() {
        // Gateway-internal rung of the timeout ladder: the global httpclient response-timeout
        // must never cut before a per-route time-limiter, so the per-route time-limiter stays
        // authoritative. Payments (15s) is the longest per-route time-limiter.
        Duration longestPerRouteTimeLimiter = timeLimiterRegistry.timeLimiter("paymentCircuitBreaker")
                .getTimeLimiterConfig().getTimeoutDuration();
        assertThat(httpClientResponseTimeout).isGreaterThanOrEqualTo(longestPerRouteTimeLimiter);
    }

    @Test
    void defaultCircuitBreakerConfig_comesFromYaml() {
        var cfg = circuitBreakerRegistry.circuitBreaker("someUnconfiguredName").getCircuitBreakerConfig();
        assertThat(cfg.getSlidingWindowSize()).isEqualTo(20);
        assertThat(cfg.getFailureRateThreshold()).isEqualTo(60f);
        assertThat(cfg.getMinimumNumberOfCalls()).isEqualTo(20);
    }

    @Test
    void fraudInstance_comesFromYaml() {
        var cb = circuitBreakerRegistry.circuitBreaker("fraudCircuitBreaker").getCircuitBreakerConfig();
        assertThat(cb.getFailureRateThreshold()).isEqualTo(50f);

        var tl = timeLimiterRegistry.timeLimiter("fraudCircuitBreaker").getTimeLimiterConfig();
        assertThat(tl.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void financialInstances_haveTheirYamlTimeouts() {
        assertThat(timeLimiterRegistry.timeLimiter("transactionsCircuitBreaker")
                .getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(10));
        assertThat(timeLimiterRegistry.timeLimiter("paymentCircuitBreaker")
                .getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void breaker_recordsOnlyTimeoutsAndTransportErrors_notClientStatusExceptions() {
        var predicate = circuitBreakerRegistry.circuitBreaker("someUnconfiguredName")
                .getCircuitBreakerConfig().getRecordExceptionPredicate();

        // Should count toward tripping the breaker:
        assertThat(predicate.test(new TimeoutException())).isTrue();
        assertThat(predicate.test(new ConnectException("refused"))).isTrue();   // IOException subtype

        // Must NOT trip the breaker (client-status exceptions — incl. 429/503 responses):
        assertThat(predicate.test(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))).isFalse();
        assertThat(predicate.test(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS))).isFalse();
    }
}