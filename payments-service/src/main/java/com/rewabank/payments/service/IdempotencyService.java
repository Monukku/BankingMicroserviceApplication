package com.rewabank.payments.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String PREFIX = "pymt:idem:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + key));
    }

    public void store(String key, String paymentOrderId) {
        redisTemplate.opsForValue().set(PREFIX + key, paymentOrderId, TTL);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(PREFIX + key);
    }
}