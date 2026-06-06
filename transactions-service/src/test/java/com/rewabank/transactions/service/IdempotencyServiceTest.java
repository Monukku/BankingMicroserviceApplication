package com.rewabank.transactions.service;

import com.rewabank.transactions.entity.Transaction;
import com.rewabank.transactions.exception.TransactionException;
import com.rewabank.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private IdempotencyService idempotencyService;

    private static final String KEY    = "idem-key-001";
    private static final String PREFIX = "idempotency:txn:";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ── checkExisting ─────────────────────────────────────────────────────────

    @Test
    void checkExisting_ShouldReturnEmpty_WhenKeyIsNew() {
        when(valueOperations.get(PREFIX + KEY)).thenReturn(null);
        when(transactionRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());

        Optional<Transaction> result = idempotencyService.checkExisting(KEY);

        assertTrue(result.isEmpty());
    }

    @Test
    void checkExisting_ShouldThrow_WhenKeyIsInProgress() {
        when(valueOperations.get(PREFIX + KEY)).thenReturn("IN_PROGRESS");

        TransactionException ex = assertThrows(TransactionException.class,
                () -> idempotencyService.checkExisting(KEY));
        assertEquals("TXN_001", ex.getErrorCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void checkExisting_ShouldReturnTransaction_WhenKeyExistsInDb() {
        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(KEY)
                .status(Transaction.TransactionStatus.COMPLETED)
                .build();
        when(valueOperations.get(PREFIX + KEY)).thenReturn(null);
        when(transactionRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(existing));

        Optional<Transaction> result = idempotencyService.checkExisting(KEY);

        assertTrue(result.isPresent());
        assertEquals(KEY, result.get().getIdempotencyKey());
    }

    @Test
    void checkExisting_ShouldQueryDb_WhenRedisHasNonProgressValue() {
        // Redis has a completed txn ID (not IN_PROGRESS) — still falls through to DB
        when(valueOperations.get(PREFIX + KEY)).thenReturn("some-txn-id");
        Transaction existing = Transaction.builder().id(UUID.randomUUID()).build();
        when(transactionRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(existing));

        Optional<Transaction> result = idempotencyService.checkExisting(KEY);

        assertTrue(result.isPresent());
    }

    // ── markInProgress ────────────────────────────────────────────────────────

    @Test
    void markInProgress_ShouldSetInProgressInRedis_WithTwentyFourHourTtl() {
        idempotencyService.markInProgress(KEY);

        verify(valueOperations).set(PREFIX + KEY, "IN_PROGRESS", Duration.ofHours(24));
    }

    // ── markCompleted ─────────────────────────────────────────────────────────

    @Test
    void markCompleted_ShouldSetTransactionIdInRedis_WithTwentyFourHourTtl() {
        String txnId = UUID.randomUUID().toString();

        idempotencyService.markCompleted(KEY, txnId);

        verify(valueOperations).set(PREFIX + KEY, txnId, Duration.ofHours(24));
    }

    // ── release ───────────────────────────────────────────────────────────────

    @Test
    void release_ShouldDeleteKeyFromRedis() {
        idempotencyService.release(KEY);

        verify(redisTemplate).delete(PREFIX + KEY);
    }
}