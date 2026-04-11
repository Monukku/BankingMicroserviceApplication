package com.rewabank.transactions.service;

import com.rewabank.transactions.dto.TransactionResponse;
import com.rewabank.transactions.entity.Transaction;
import com.rewabank.transactions.exception.TransactionException;
import com.rewabank.transactions.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Idempotency service.
 * Two-layer check:
 * 1. Redis (fast — 24hr TTL) — for in-flight duplicate detection
 * 2. DB (authoritative) — for completed transaction lookup
 *
 * If key exists in Redis → return cached response immediately
 * If key exists in DB   → return stored transaction
 * If new key           → proceed with transaction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final TransactionRepository         transactionRepository;

    private static final String PREFIX         = "idempotency:txn:";
    private static final Duration TTL          = Duration.ofHours(24);
    private static final String IN_PROGRESS    = "IN_PROGRESS";

    // Check if idempotency key already used
    public Optional<Transaction> checkExisting(String idempotencyKey) {
        // Check Redis first (fast path)
        Object cached = redisTemplate.opsForValue().get(PREFIX + idempotencyKey);
        if (cached != null) {
            if (IN_PROGRESS.equals(cached.toString())) {
                throw new TransactionException("TXN_001",
                        "Transaction with this idempotency key is already in progress");
            }
        }
        // Check DB (authoritative)
        return transactionRepository.findByIdempotencyKey(idempotencyKey);
    }

    // Mark key as in-progress
    public void markInProgress(String idempotencyKey) {
        redisTemplate.opsForValue()
                .set(PREFIX + idempotencyKey, IN_PROGRESS, TTL);
        log.debug("Idempotency key marked IN_PROGRESS: {}", idempotencyKey);
    }

    // Mark key as completed with transaction ID
    public void markCompleted(String idempotencyKey, String transactionId) {
        redisTemplate.opsForValue()
                .set(PREFIX + idempotencyKey, transactionId, TTL);
        log.debug("Idempotency key completed: {} txnId: {}",
                idempotencyKey, transactionId);
    }

    // Release key on failure so client can retry with same key
    public void release(String idempotencyKey) {
        redisTemplate.delete(PREFIX + idempotencyKey);
        log.debug("Idempotency key released: {}", idempotencyKey);
    }
}
