# RewaBank — Fix Summary

---

## Critical (5 fixes)

| # | Issue | Service | Root Cause | Files Changed | Fix Applied |
|---|-------|---------|-----------|---------------|-------------|
| 1 | Daily limit race condition | transactions-service | `validateAndConsumeLimit()` read then wrote without a DB lock — two concurrent threads both passed the capacity check | `DailyLimitRepository.java` · `LimitService.java` · `LimitServiceTest.java` | Added `findWithLockByKeycloakUserIdAndLimitDate()` with `@Lock(PESSIMISTIC_WRITE)`; `saveAndFlush` + `DataIntegrityViolationException` catch for concurrent first-insert |
| 2 | Internal debit/credit no Java-level auth | accounts-service | `/accounts/{id}/debit` and `/credit` relied only on Istio mTLS — any authenticated user could call them if mTLS misconfigured | `SecurityConfig.java` · `InternalAccountController.java` · `InternalAccountControllerSecurityTest.java` *(new)* | URL matcher requiring `ROLE_TRANSACTIONS_MS` in `SecurityConfig`; `@PreAuthorize("hasRole('TRANSACTIONS_MS')")` on both methods |
| 3 | BCrypt cost 10 on OTP | auth-service | `new BCryptPasswordEncoder(10)` hardcoded — 1024 iterations on every OTP verify (every transfer/card op) | `SecurityConfig.java` · `OtpService.java` · `application.yaml` · `application-test.properties` · `OtpServiceTest.java` | Extracted `@Bean PasswordEncoder` with `${otp.bcrypt-strength:5}`; default cost reduced from 10 → 5 |
| 4 | Account number non-uniform random | accounts-service | `(long)(nextDouble() × 900B)` — `double` precision causes clustering in certain ranges | `AccountService.java` | Replaced with `100_000_000_000L + secureRandom.nextLong(900_000_000_000L)` |
| 5 | Saga missing compensation after ledger failure | transactions-service | If `createLedgerEntries()` threw after debit+credit already succeeded via Feign, Spring rolled back the DB but money stayed moved | `TransferSaga.java` · `TransferSagaTest.java` | `createLedgerEntries()` wrapped in try-catch; on failure, new `compensateFullTransfer()` reverses both sides (debit destination + credit source); throws `TXN_006` |

---

## High (7 fixes)

| # | Issue | Service | Root Cause | Files Changed | Fix Applied |
|---|-------|---------|-----------|---------------|-------------|
| 6 | Transaction reversal — no service-layer auth | transactions-service | `@PreAuthorize` only on controller; `TransactionService.reverse()` had none | `TransactionService.java` | Added `@PreAuthorize("hasAnyRole('BRANCH_MANAGER','SUPER_ADMIN')")` on service method |
| 7 | Self-transfer not prevented | transactions-service | `TransferRequest` had no cross-field validation | `TransferRequest.java` | Added `@AssertTrue isDifferentAccounts()` + `@Size(max=255)` on `deviceId` |
| 8 | Kafka DLQ silent failures | fraud-service | `@RetryableTopic` configured `.dlq` topics but nothing consumed from them — failed events silently accumulated | `FraudDlqConsumer.java` *(new)* · `FraudDlqConsumerTest.java` *(new)* | New `@KafkaListener` on both DLQ topics; creates BLOCK fraud alert (score=99) with DLQ topic in `triggeredRules`; exception-safe |
| 9 | Outbox retry no backoff | accounts-service | On Kafka failure, events retried every 5 s with no delay — hammered broker during downtime | `OutboxEvent.java` · `V5__add_outbox_next_retry_at.sql` *(new)* · `OutboxEventRepository.java` · `OutboxPublisherService.java` · `OutboxPublisherServiceTest.java` *(new)* | Exponential backoff: `min(2^retries × 5s, 300s)`; delays 10 s → 20 s → 40 s → 80 s; `next_retry_at` column added |
| 10 | Feign null `getMessage()` | transactions-service | Both catch blocks called `e.getMessage()` unconditionally — null message produced `"Debit failed: null"` | `TransferSaga.java` | `e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()` in both catch blocks |
| 11 | `AccountDebitCreditRequest` no validation | accounts-service | Record had no Bean Validation — null `amount` or blank `correlationId` reached DB layer | `AccountDebitCreditRequest.java` | `@NotNull @DecimalMin("0.01")` on `amount`; `@NotBlank` on `correlationId` |
| 12 | Keycloak role assignment swallows exception | auth-service | `assignRole()` caught all exceptions and only logged — user created with no roles, permanently locked out, email blocked for re-registration | `KeycloakUserService.java` · `KeycloakUserServiceTest.java` | `assignRole()` now propagates exceptions; `createUser()` catches failure, calls `deleteUser()` to remove orphan, throws `AUTH_002`; fixed test mock chain that was masking the bug |

---

## Medium (6 fixes)

| # | Issue | Service | Root Cause | Files Changed | Fix Applied |
|---|-------|---------|-----------|---------------|-------------|
| 13 | CVV BCrypt cost 10 | cards-service | `new BCryptPasswordEncoder(10)` hardcoded — slow hash on every card transaction | `SecurityConfig.java` · `CardService.java` · `application.yml` | `@Bean PasswordEncoder cvvPasswordEncoder()` with `${card.cvv.bcrypt-strength:5}`; default cost 10 → 5 |
| 14 | No max page size | transactions-service | `@PageableDefault(size=20)` but client could request `?size=1000000` | `application.yml` | `spring.data.web.pageable.max-page-size: 100` |
| 15 | Fraud alert threshold hardcoded | fraud-service | `if (result.score() >= 80)` magic number — cannot adjust without redeploying | `FraudEventConsumer.java` · `application.yaml` | `@Value("${fraud.alert.threshold:80}") int alertThreshold`; property added to config |
| 16 | Activation allows duplicate active account type | accounts-service | `activateAccount()` checked KYC but not whether customer already had an active account of the same type | `AccountsRepository.java` · `AccountService.java` | Added `existsByCustomerIdAndAccountTypeAndStatus...()` query; guard throws `ACCT_003` before KYC check |
| 17 | `DataIntegrityViolationException` returns 500 | accounts-service | DB constraint violations fell through to generic `Exception` handler | `GlobalExceptionHandler.java` | Dedicated `@ExceptionHandler(DataIntegrityViolationException.class)` returning HTTP 409 with `ACCT_CONFLICT` |
| 18 | OTP rate-limit constants hardcoded | auth-service | `MAX_OTP_ATTEMPTS=3`, rate window=10 min, lock=15 min all hardcoded — cannot tune without recompile | `OtpService.java` · `application.yaml` · `application-test.properties` · `OtpServiceTest.java` | `@Value` for all three: `otp.max-attempts`, `otp.rate-window-minutes`, `otp.lock-minutes` |

---

## Low (7 fixes)

| # | Issue | Service | Root Cause | Files Changed | Fix Applied |
|---|-------|---------|-----------|---------------|-------------|
| 19 | No idempotency on card issuance | cards-service | Retry after timeout issued a second card | `Card.java` · `V3__add_card_idempotency.sql` *(new)* · `CardRepository.java` · `CardService.java` · `CardController.java` | Optional `X-Idempotency-Key` header; `idempotency_key` column + unique index; service returns existing card on duplicate key |
| 20 | No Micrometer timer on fraud scoring | fraud-service | Latency logged only as WARN — no Grafana metric for SLA compliance | `FraudScoringService.java` | `MeterRegistry` injected; `Timer.Sample` wraps `score()` with `fraud.scoring.duration` metric tagged by `action` |
| 21 | CANCELLED cards returned by `getById` | cards-service | `findCardForUser()` returned cards regardless of status | `CardService.java` | `getById()` throws `CARD_001` (404) when card status is `CANCELLED` |
| 22 | `nameOnCard` no size limit | cards-service | Physical card chips hold max 26 characters — no validation enforced this | `CardIssueRequest.java` | `@Size(max=26)` on `nameOnCard` |
| 23 | No account ownership check on card issuance | cards-service | `accountId` from request used without verifying it belonged to the caller | `CardService.java` | Calls `accountsFeignClient.getAccount()` and compares `keycloakUserId`; throws `CARD_003` on mismatch |
| 24 | Inconsistent error codes across services | all | No shared error code registry | — | **Skipped** — requires new shared BOM module; architectural change |
| 25 | Trace ID not confirmed in Feign calls | all | Concern that trace headers were not propagated | — | **No change needed** — Spring Boot 3 + Micrometer Tracing + OTel bridge propagates W3C trace headers through Feign automatically |

---

## Test Coverage Gaps Closed

| Gap | Service | Test Class | Tests Added | Notes |
|-----|---------|------------|-------------|-------|
| Debit/credit business-logic correctness | accounts-service | `AccountConcurrencyTest` *(new)* | 9 | Sequential balance exhaustion, min-balance floor, over-limit single debit, zero/negative amounts, frozen account, credit accumulation, interleaved ops. DB-level lock testing requires Testcontainers + PostgreSQL (H2 does not support Hibernate 6's `FOR NO KEY UPDATE`) |
| Cache invalidation | accounts-service | `AccountReadServiceCacheTest` *(new)* | 8 | `@Cacheable` miss vs hit; `@CacheEvict` forces DB re-read; `@CachePut` updates cache without DB read. Uses `@TestConfiguration @EnableCaching` + `ConcurrentMapCacheManager` |
| Outbox retry/backoff | accounts-service | `OutboxPublisherServiceTest` *(new)* | 6 | Success → PUBLISHED; first failure → 10 s backoff; doubling → 20 s; 4th retry → 80 s; 5th → FAILED; empty batch no-op |
| Context loads | transactions-service | `TransactionServiceApplicationTests` *(recreated)* | 1 | Recreated deleted file; created `application-test.properties` for the service (H2, Redis/Kafka excluded, mock JWT URIs) |

---

## Keycloak Action Required

> The fix for issue **#2** requires a manual step in Keycloak:
> 1. Create realm role `TRANSACTIONS_MS` in the `rewabank` realm
> 2. Assign it to the `transactions-ms` service account
> 3. Configure the transactions-ms Feign client to use client-credentials OAuth2 flow when calling the internal accounts-service endpoints
