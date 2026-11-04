package com.rewabank.gateway.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the rate-limiter's Redis bootstrap against the istio sidecar startup race:
 * the app container can initialise before Envoy has programmed iptables/DNS, so the
 * very first {@code RedisClient.connect()} may fail with a transient
 * {@link RedisConnectionException}. The connect must retry with backoff instead of
 * killing the Spring context (which previously crash-looped the gateway at 1/2).
 */
class RateLimiterConfigTest {

    @SuppressWarnings("unchecked")
    @Test
    void connectWithRetry_retriesTransientFailures_thenSucceeds() {
        RateLimiterConfig cfg = new RateLimiterConfig();
        ReflectionTestUtils.setField(cfg, "connectMaxAttempts", 5);
        ReflectionTestUtils.setField(cfg, "connectRetryDelayMs", 0L);

        RedisClient client = mock(RedisClient.class);
        StatefulRedisConnection<String, byte[]> conn = mock(StatefulRedisConnection.class);
        when(client.connect(any(RedisCodec.class)))
                .thenThrow(new RedisConnectionException("sidecar not ready"))
                .thenThrow(new RedisConnectionException("sidecar not ready"))
                .thenReturn(conn);

        StatefulRedisConnection<String, byte[]> result = cfg.connectWithRetry(client);

        assertThat(result).isSameAs(conn);
        verify(client, times(3)).connect(any(RedisCodec.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void connectWithRetry_exhaustsAttempts_thenRethrows() {
        RateLimiterConfig cfg = new RateLimiterConfig();
        ReflectionTestUtils.setField(cfg, "connectMaxAttempts", 3);
        ReflectionTestUtils.setField(cfg, "connectRetryDelayMs", 0L);

        RedisClient client = mock(RedisClient.class);
        when(client.connect(any(RedisCodec.class)))
                .thenThrow(new RedisConnectionException("redis down"));

        assertThatThrownBy(() -> cfg.connectWithRetry(client))
                .isInstanceOf(RedisConnectionException.class);
        verify(client, times(3)).connect(any(RedisCodec.class));
    }
}