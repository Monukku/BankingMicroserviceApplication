package com.rewabank.gateway.config;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
public class RateLimiterConfig {

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
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        return LettuceBasedProxyManager.<String>builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(5)))
                .build();
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