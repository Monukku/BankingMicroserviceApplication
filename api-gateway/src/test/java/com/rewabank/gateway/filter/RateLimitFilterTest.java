package com.rewabank.gateway.filter;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.AsyncBucketProxy;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock LettuceBasedProxyManager<String> proxyManager;
    @Mock AsyncProxyManager<String>        asyncProxyManager;
    @Mock AsyncBucketProxy                 asyncBucketProxy;
    @Mock ConsumptionProbe                 probe;
    @Mock GatewayFilterChain               chain;

    private RateLimitFilter filter;
    private BucketConfiguration generalConfig;
    private BucketConfiguration financialConfig;
    private BucketConfiguration authConfig;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();

        // Minimal BucketConfiguration stubs (capacity doesn't matter — probe is mocked)
        generalConfig   = BucketConfiguration.builder()
                .addLimit(io.github.bucket4j.Bandwidth.builder().capacity(20)
                        .refillGreedy(20, java.time.Duration.ofSeconds(1)).build()).build();
        financialConfig = BucketConfiguration.builder()
                .addLimit(io.github.bucket4j.Bandwidth.builder().capacity(5)
                        .refillGreedy(5, java.time.Duration.ofSeconds(1)).build()).build();
        authConfig      = BucketConfiguration.builder()
                .addLimit(io.github.bucket4j.Bandwidth.builder().capacity(10)
                        .refillGreedy(10, java.time.Duration.ofSeconds(60)).build()).build();

        ReflectionTestUtils.setField(filter, "proxyManager",   proxyManager);
        ReflectionTestUtils.setField(filter, "generalConfig",  generalConfig);
        ReflectionTestUtils.setField(filter, "financialConfig", financialConfig);
        ReflectionTestUtils.setField(filter, "authConfig",     authConfig);
        ReflectionTestUtils.setField(filter, "trustedProxyHops", 1);
    }

    // ── Disabled path ────────────────────────────────────────────────────────

    @Test
    void filter_disabled_chainsWithoutRateLimitCheck() {
        RateLimitFilter disabled = new RateLimitFilter(); // proxyManager = null
        MockServerWebExchange exchange = exchange("/api/v1/accounts");
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(disabled.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
        verifyNoInteractions(proxyManager);
    }

    // ── OPTIONS preflight ────────────────────────────────────────────────────

    @Test
    void filter_optionsPreflight_chainsWithoutRateLimitCheck() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/accounts").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
        verifyNoInteractions(proxyManager);
    }

    // ── Allowed requests ─────────────────────────────────────────────────────

    @Test
    void filter_authenticatedRequest_allowed_setsRemainingHeader() {
        stubBucketAllowed(19L);
        MockServerWebExchange exchange = exchange("/api/v1/accounts");
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth("user-1")))
        ).verifyComplete();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("19");
    }

    @Test
    void filter_unauthenticatedRequest_allowed_usesIpBasedKey() {
        stubBucketAllowed(4L);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .remoteAddress(new java.net.InetSocketAddress("192.168.1.10", 0))
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        // No security context → IP-based key
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
        // Verify the proxy was called with an ip: prefixed key
        verify(asyncProxyManager).getProxy(argThat(k -> k.startsWith("ip:")), any());
    }

    @Test
    void filter_xForwardedFor_usesTrustedRightmostHop_notSpoofableLeftmost() {
        stubBucketAllowed(4L);
        // "10.0.0.1" is the client-supplied (spoofable) left-most value; "172.16.0.1" was
        // appended by our trusted proxy. With 1 trusted hop we must key on the latter.
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "10.0.0.1, 172.16.0.1")
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(asyncProxyManager).getProxy(eq("ip:172.16.0.1:auth"), any());
    }

    @Test
    void filter_xForwardedFor_spoofedLeftmostIsIgnored() {
        stubBucketAllowed(4L);
        // An attacker prepends a rotating value to dodge the per-IP limit — it must NOT be used.
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "6.6.6.6, 10.0.0.1, 172.16.0.1")
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(asyncProxyManager, never()).getProxy(argThat(k -> k.contains("6.6.6.6")), any());
        verify(asyncProxyManager).getProxy(eq("ip:172.16.0.1:auth"), any());
    }

    @Test
    void filter_prefersEnvoyExternalAddress_overXff() {
        stubBucketAllowed(4L);
        // Envoy sets x-envoy-external-address itself (unspoofable) — it wins over any XFF.
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .header("x-envoy-external-address", "203.0.113.5")
                        .header("X-Forwarded-For", "6.6.6.6, 172.16.0.1")
                        .build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(asyncProxyManager).getProxy(eq("ip:203.0.113.5:auth"), any());
    }

    // ── Per-tier bucket key (tiers must not share one bucket) ─────────────────

    @Test
    void filter_bucketKey_isPerTier_notSharedAcrossTiers() {
        // Same user, a general path vs a financial path, must use DISTINCT buckets so one
        // tier draining cannot lock out another.
        stubBucketAllowed(9L);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange("/api/v1/accounts"), chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth("user-9")))
        ).verifyComplete();
        StepVerifier.create(
                filter.filter(exchange("/api/v1/transactions/transfer"), chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth("user-9")))
        ).verifyComplete();

        verify(asyncProxyManager).getProxy(eq("user:user-9:general"), any());
        verify(asyncProxyManager).getProxy(eq("user:user-9:financial"), any());
    }

    // ── Rate limited ─────────────────────────────────────────────────────────

    @Test
    void filter_rateLimited_returns429WithRetryAfterHeader() {
        stubBucketDenied(2_000_000_000L); // 2s in nanos → Retry-After: 3
        MockServerWebExchange exchange = exchange("/api/v1/accounts");

        // Rejection is signalled as a 429 ResponseStatusException. Because this filter
        // runs OUTSIDE the circuit breaker (see getOrder), GlobalExceptionHandler turns
        // it into an honest 429 — the breaker never sees it, so it cannot be counted as
        // a downstream failure nor laundered into a 503.
        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth("user-2")))
        ).expectErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(ResponseStatusException.class);
            assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(TOO_MANY_REQUESTS);
        }).verify();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("3");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
    }

    // ── Ordering — must run before the circuit breaker ────────────────────────

    @Test
    void filter_order_isBeforeAnyPerRouteFilter() {
        // Per-route gateway filters (incl. circuitBreaker) are ordered from 1 upward.
        // A negative order guarantees this global rate limiter executes outside — ahead
        // of — the circuit breaker, so a 429 can never reach or trip the breaker.
        assertThat(filter.getOrder()).isEqualTo(RateLimitFilter.ORDER);
        assertThat(filter.getOrder()).isLessThan(1);
    }

    // ── Bucket config selection ───────────────────────────────────────────────

    @Test
    void filter_authPath_usesAuthBucketConfig() {
        stubBucketAllowed(9L);
        MockServerWebExchange exchange = exchange("/api/v1/auth/login");
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        // authConfig was supplied to getProxy — verify via captured supplier
        verify(asyncProxyManager).getProxy(anyString(),
                argThat(supplier -> {
                    try { return supplier.get().get() == authConfig; } catch (Exception e) { return false; }
                }));
    }

    @Test
    void filter_financialPath_usesFinancialBucketConfig() {
        stubBucketAllowed(4L);
        MockServerWebExchange exchange = exchange("/api/v1/transactions/transfer");
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth("user-3")))
        ).verifyComplete();

        verify(asyncProxyManager).getProxy(anyString(),
                argThat(supplier -> {
                    try { return supplier.get().get() == financialConfig; } catch (Exception e) { return false; }
                }));
    }

    @Test
    void filter_generalPath_usesGeneralBucketConfig() {
        stubBucketAllowed(19L);
        MockServerWebExchange exchange = exchange("/api/v1/loans/apply");
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(jwtAuth("user-4")))
        ).verifyComplete();

        verify(asyncProxyManager).getProxy(anyString(),
                argThat(supplier -> {
                    try { return supplier.get().get() == generalConfig; } catch (Exception e) { return false; }
                }));
    }

    @Test
    void filter_unauthenticatedNonAuthPath_usesFinancialConfig() {
        stubBucketAllowed(4L);
        MockServerWebExchange exchange = exchange("/api/v1/accounts");
        when(chain.filter(any())).thenReturn(Mono.empty());

        // No security context → ip: key → financial config
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(asyncProxyManager).getProxy(anyString(),
                argThat(supplier -> {
                    try { return supplier.get().get() == financialConfig; } catch (Exception e) { return false; }
                }));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private JwtAuthenticationToken jwtAuth(String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private void stubBucketAllowed(long remaining) {
        when(proxyManager.asAsync()).thenReturn(asyncProxyManager);
        when(asyncProxyManager.getProxy(anyString(), any())).thenReturn(asyncBucketProxy);
        when(asyncBucketProxy.tryConsumeAndReturnRemaining(anyLong()))
                .thenReturn(CompletableFuture.completedFuture(probe));
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(remaining);
    }

    private void stubBucketDenied(long nanosToWait) {
        when(proxyManager.asAsync()).thenReturn(asyncProxyManager);
        when(asyncProxyManager.getProxy(anyString(), any())).thenReturn(asyncBucketProxy);
        when(asyncBucketProxy.tryConsumeAndReturnRemaining(anyLong()))
                .thenReturn(CompletableFuture.completedFuture(probe));
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(nanosToWait);
    }
}