package com.rewabank.gateway.config;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
public class RateLimiterConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterConfig.class);

    // ── Startup resilience: survive the istio sidecar race (Envoy not yet ready) ──

    @Value("${rate-limiting.redis.connect-max-attempts:10}")
    private int connectMaxAttempts;

    @Value("${rate-limiting.redis.connect-retry-delay-ms:3000}")
    private long connectRetryDelayMs;

    // ── Capacities ──────────────────────────────────────────────────────────

    @Value("${rate-limiting.general.capacity:20}")
    private int generalCapacity;

    @Value("${rate-limiting.general.refill-period-seconds:1}")
    private int generalPeriodSeconds;

    @Value("${rate-limiting.financial.capacity:5}")
    private int financialCapacity;

    @Value("${rate-limiting.financial.refill-period-seconds:1}")
    private int financialPeriodSeconds;

    @Value("${rate-limiting.auth.capacity:10}")
    private int authCapacity;

    @Value("${rate-limiting.auth.refill-period-seconds:60}")
    private int authPeriodSeconds;

    // ── Proxy manager (Redis-backed, conditional on rate-limiting being enabled) ─

    @Bean
    @ConditionalOnProperty(name = "rate-limiting.enabled", havingValue = "true", matchIfMissing = true)
    public LettuceBasedProxyManager<String> rateLimiterProxyManager(
            LettuceConnectionFactory lettuceConnectionFactory) {
        RedisClient redisClient = (RedisClient) lettuceConnectionFactory.getNativeClient();
        StatefulRedisConnection<String, byte[]> connection = connectWithRetry(redisClient);
        return LettuceBasedProxyManager.<String>builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(5)))
                .build();
    }

    /**
     * Opens the rate-limiter's Redis connection, retrying transient connection failures.
     * On pod startup the app container can initialise before the istio sidecar has
     * programmed iptables/DNS, so the first connect may fail ("&lt;unresolved&gt;" /
     * "connection reset by peer"). Retrying with a short backoff lets the gateway ride
     * out that window instead of crash-looping the Spring context.
     */
    StatefulRedisConnection<String, byte[]> connectWithRetry(RedisClient redisClient) {
        RedisConnectionException last = null;
        for (int attempt = 1; attempt <= connectMaxAttempts; attempt++) {
            try {
                return redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
            } catch (RedisConnectionException ex) {
                last = ex;
                log.warn("Rate-limiter Redis connect attempt {}/{} failed: {}",
                        attempt, connectMaxAttempts, ex.getMessage());
                if (attempt < connectMaxAttempts && connectRetryDelayMs > 0) {
                    try {
                        Thread.sleep(connectRetryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw (last != null) ? last
                : new IllegalStateException("Redis connect failed with no captured exception");
    }

    // ── Bucket configurations ────────────────────────────────────────────────

    @Bean(name = "generalRateLimitConfig")
    @ConditionalOnProperty(name = "rate-limiting.enabled", havingValue = "true", matchIfMissing = true)
    public BucketConfiguration generalRateLimitConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(generalCapacity)
                        .refillGreedy(generalCapacity, Duration.ofSeconds(generalPeriodSeconds))
                        .build())
                .build();
    }

    @Bean(name = "financialRateLimitConfig")
    @ConditionalOnProperty(name = "rate-limiting.enabled", havingValue = "true", matchIfMissing = true)
    public BucketConfiguration financialRateLimitConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(financialCapacity)
                        .refillGreedy(financialCapacity, Duration.ofSeconds(financialPeriodSeconds))
                        .build())
                .build();
    }

    @Bean(name = "authRateLimitConfig")
    @ConditionalOnProperty(name = "rate-limiting.enabled", havingValue = "true", matchIfMissing = true)
    public BucketConfiguration authRateLimitConfig() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(authCapacity)
                        .refillGreedy(authCapacity, Duration.ofSeconds(authPeriodSeconds))
                        .build())
                .build();
    }
}