package com.rewabank.gateway.filter;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.AsyncBucketProxy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Global rate-limiting filter backed by bucket4j + Redis (Lettuce).
 *
 * Tiers:
 *   auth      — 10 req/min  per source IP  (brute-force protection)
 *   financial — 5  req/s    per user ID    (transactions + payments)
 *   general   — 20 req/s    per user ID    (all other authenticated paths)
 *   fallback  — IP-based using financial config when user cannot be resolved
 *
 * Disabled (passes through) when rate-limiting.enabled=false or when the
 * LettuceBasedProxyManager bean is absent (e.g., in unit tests).
 *
 * <h2>Ordering — runs BEFORE the circuit breaker</h2>
 * Rate limiting is admission control (a client-behaviour concern), not downstream
 * health. It MUST execute outside the per-route Resilience4j circuit breaker so a
 * 429 rejection never (a) counts as a downstream failure that could trip the breaker
 * for every client, nor (b) gets laundered into a 503 by the breaker's fallback.
 * Per-route gateway filters (including {@code circuitBreaker}) are ordered from 1
 * upward, so this global filter uses a negative order to sort ahead of all of them.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    /**
     * Must be lower than any per-route gateway filter (which start at 1) so the
     * rate-limit decision is made before — i.e. outside — the circuit breaker.
     */
    static final int ORDER = -10;

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * Number of trusted reverse proxies (Istio ingress + sidecars) in front of this
     * gateway. The client IP is read this many hops in from the RIGHT of X-Forwarded-For;
     * the left-most entries are client-supplied and must never be trusted — otherwise a
     * caller can defeat the per-IP limit by prepending a rotating X-Forwarded-For value.
     * Override per environment to match the real proxy chain.
     */
    @Value("${rate-limiting.trusted-proxy-hops:1}")
    private int trustedProxyHops = 1;

    @Autowired(required = false)
    private LettuceBasedProxyManager<String> proxyManager;

    @Autowired(required = false)
    @Qualifier("generalRateLimitConfig")
    private BucketConfiguration generalConfig;

    @Autowired(required = false)
    @Qualifier("financialRateLimitConfig")
    private BucketConfiguration financialConfig;

    @Autowired(required = false)
    @Qualifier("authRateLimitConfig")
    private BucketConfiguration authConfig;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (proxyManager == null) {
            return chain.filter(exchange);
        }

        // Preflight requests are not subject to rate limiting
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        String ip   = resolveClientIp(exchange);

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> {
                    if (ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth) {
                        String subject = jwtAuth.getToken().getSubject();
                        return subject != null ? "user:" + subject : "ip:" + ip;
                    }
                    return "ip:" + ip;
                })
                .defaultIfEmpty("ip:" + ip)
                .flatMap(rateLimitKey -> applyLimit(exchange, chain, path, rateLimitKey));
    }

    private Mono<Void> applyLimit(ServerWebExchange exchange, GatewayFilterChain chain,
                                  String path, String principalKey) {
        // Key the bucket per (principal, tier) so the auth/financial/general limits stay
        // independent. Keying on the principal alone collapses every tier into one shared
        // bucket, because bucket4j applies the supplied config only when the bucket is
        // first created — so whichever path a user hit first would pin all later requests.
        String tier = resolveTier(path, principalKey);
        BucketConfiguration config = configForTier(tier);
        String bucketKey = principalKey + ":" + tier;
        AsyncBucketProxy bucket = proxyManager.asAsync()
                .getProxy(bucketKey, () -> CompletableFuture.completedFuture(config));

        return Mono.fromFuture(bucket.tryConsumeAndReturnRemaining(1))
                .flatMap(probe -> {
                    if (probe.isConsumed()) {
                        exchange.getResponse().getHeaders()
                                .set("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
                        return chain.filter(exchange);
                    }
                    long retryAfterSecs =
                            TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
                    exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfterSecs));
                    exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", "0");
                    log.debug("Rate limit exceeded for key={} path={}", bucketKey, path);
                    // Signal rejection with a 429. Because this filter runs OUTSIDE the
                    // circuit breaker (see class javadoc / getOrder), the error is handled
                    // by GlobalExceptionHandler as an honest 429 — it is never seen by the
                    // breaker, so it cannot trip it or be laundered into a 503.
                    return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS));
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private String resolveTier(String path, String principalKey) {
        if (path.startsWith("/api/v1/auth/")) {
            return "auth";
        }
        if (path.startsWith("/api/v1/transactions/") || path.startsWith("/api/v1/payments/")) {
            return "financial";
        }
        // Unauthenticated callers (ip: key) get the stricter financial limit.
        if (principalKey.startsWith("ip:")) {
            return "financial";
        }
        return "general";
    }

    private BucketConfiguration configForTier(String tier) {
        switch (tier) {
            case "auth":      return authConfig;
            case "financial": return financialConfig;
            default:          return generalConfig;
        }
    }

    /**
     * Resolves the client IP for the rate-limit key WITHOUT trusting client-supplied
     * header values. See {@link #trustedProxyHops}.
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();

        // 1. x-envoy-external-address is set by our own Istio/Envoy ingress to the trusted
        //    downstream client address. A mesh component sets it, so the client cannot
        //    forge it — prefer it when present.
        String envoyAddr = headers.getFirst("x-envoy-external-address");
        if (envoyAddr != null && !envoyAddr.isBlank()) {
            return envoyAddr.trim();
        }

        // 2. X-Forwarded-For: NEVER trust the left-most (client-settable) entry. Count
        //    trustedProxyHops in from the RIGHT — those entries were appended by proxies we
        //    control, so a prepended spoof value is ignored.
        String xff = headers.getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            int idx = hops.length - Math.max(trustedProxyHops, 1);
            if (idx < 0) idx = 0;
            String ip = hops[idx].trim();
            if (!ip.isBlank()) {
                return ip;
            }
        }

        // 3. Fall back to the direct connection peer.
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress().getHostAddress() : "unknown";
    }
}