# RewaBank — Quality Audit Fixes

Fixes applied from the comprehensive project analysis covering Security, Performance, Observability, API Design, Database, Configuration, and Testing.

---

## Critical

| # | Category | Service(s) | Issue | Files Changed | Fix Applied |
|---|----------|-----------|-------|---------------|-------------|
| 1 | Security | accounts, fraud, cards, customers, notifications, audit | `spring.json.trusted.packages: "*"` allows Kafka consumers to deserialize any Java class — Remote Code Execution risk if malicious JSON is injected into a topic | 6 × `application.yml/yaml` | Changed to `"com.rewabank,java.util,java.time,java.math"` |
| 2 | Code Quality | cards-service | Two production `TODO` comments in `CardTransactionService.validateFraud()` — merchant ID hardcoded as `"MERCHANT_ID"`, channel hardcoded as `"UNKNOWN"`, making fraud scoring unable to differentiate by merchant or channel | `CardTransactionService.java:255-258` | Used existing entity fields: `transaction.getMerchantName()` and `transaction.getChannel().name()` |

---

## High

| # | Category | Service(s) | Issue | Files Changed | Fix Applied |
|---|----------|-----------|-------|---------------|-------------|
| 3 | Performance | accounts, transactions, loans | HikariCP connection pool undersized — accounts max=15, transactions max=20, loans max=10. Under peak concurrent load, pool exhaustion causes HTTP 503 | `application.yml` in 3 services | accounts: 15→25 (min-idle 3→5); transactions: 20→30 (min-idle 5→8); loans: 10→20 (min-idle 2→5); added `keepalive-time: 30000` to all three |
| 4 | API Design | loans-service | `@PageableDefault(size=20)` with no `max-page-size` limit — client can request `?size=1000000`, causing memory exhaustion (same fix applied to transactions-service earlier was missed here) | `application.yml` (loans) | Added `spring.data.web.pageable.max-page-size: 100` and `default-page-size: 20` |
| 5 | Testing | fraud, loans, transactions | Direct `.get(0)` on `Page.getContent()` / `List` without prior size guard — `IndexOutOfBoundsException` if mock returns fewer elements than expected | `FraudAlertServiceTest.java` (×2) · `EmiCalculatorServiceTest.java` (×3) · `TransactionServiceTest.java` (×1) | Added `assertFalse(result.getContent().isEmpty())` / `assertThat(schedule).isNotEmpty()` before each `.get(0)` call |
| 6 | API Design | api-gateway | CORS origins read from env vars `${CORS_ORIGIN_WEB}` / `${CORS_ORIGIN_MOBILE}` with no wildcard validation — if misconfigured to `*`, CORS becomes unrestricted and credentials can be exfiltrated | `SecurityConfig.java` (gateway) | Added startup guard in `corsWebFilter()` — throws `IllegalStateException` if either origin is `"*"` |

---

## Medium

| # | Category | Service(s) | Issue | Files Changed | Fix Applied |
|---|----------|-----------|-------|---------------|-------------|
| 7 | Observability | accounts, cards, loans, transactions | All `@Scheduled` tasks run silently — no Micrometer metrics, cannot detect SLA breaches, silent failures, or monitor background job health in Grafana | `DormancyScheduler.java` · `CardExpiryScheduler.java` · `OutboxScheduler.java` (accounts, loans, transactions) | Added `MeterRegistry` injection + `Timer.Sample` (try/finally) to every scheduler; metrics published: `scheduler.dormancy.duration`, `scheduler.card.expiry.alert.duration`, `scheduler.card.mark.expired.duration`, `scheduler.outbox.duration` (tagged by service) |
| 8 | Observability | All 9 services | `logging.level.com.rewabank: DEBUG` in all production configs — generates excessive log volume, degrades I/O performance, and reduces signal-to-noise ratio in log aggregation | All 9 `application.yml/yaml` | Changed to `INFO` across all services (auth, accounts, transactions, fraud, cards, loans, notifications, audit, customers) |
| 9 | Database | accounts-service | `daily_limits → accounts` FK created without `ON DELETE CASCADE` — if an account is ever hard-deleted, orphaned `daily_limits` rows remain; FK violation if deletion is attempted without prior cleanup | New `V6__fix_daily_limits_fk_cascade.sql` | New Flyway migration: drops the auto-named FK and recreates it with `ON DELETE CASCADE ON UPDATE CASCADE` |

---

## Low

| # | Category | Service(s) | Issue | Files Changed | Fix Applied |
|---|----------|-----------|-------|---------------|-------------|
| 10 | Performance | accounts, loans, transactions | Outbox scheduler polls every 5 s hardcoded — unnecessarily frequent under low load; generates DB lock contention when multiple pod replicas run simultaneously | `OutboxScheduler.java` in 3 services | Changed `@Scheduled(fixedDelay = 5000)` to `@Scheduled(fixedDelayString = "${outbox.publish.interval-ms:10000}")` — default 10 s, tunable via K8s ConfigMap without redeployment |
| 11 | Configuration | accounts, transactions, loans | HikariCP has no keepalive configured — stale connections from network partitions are not detected until query execution fails | `application.yml` in 3 services | Added `keepalive-time: 30000` (HikariCP pings the DB every 30 s to proactively refresh idle connections) |
| 12 | Architecture | accounts, transactions, loans | Resilience4j circuit-breaker thresholds (`sliding-window-size`, `failure-rate-threshold`, `wait-duration`) hardcoded in YAML — requires redeployment to tune in production | `application.yml` in 3 services | All thresholds now use `${ENV_VAR:default}` syntax; can be overridden at runtime via K8s ConfigMap or environment variables without rebuilding the image |

---

## Not Changed (Findings Verified as Already Correct)

| # | Finding | Reason Not Changed |
|---|---------|-------------------|
| A | Audit log partitions missing | `V1__create_audit_log.sql` already has all 12 monthly partitions for 2026 plus a default catch-all partition |
| B | AES key not validated at startup | `EncryptionConfig.aesSecretKey()` is a `@Bean` method — Spring calls it eagerly at startup; invalid Base64 or wrong key length already throws `IllegalArgumentException` before the app accepts traffic |
| C | `CardTransactionService` error codes not specific | Already uses specific error codes: `CARD_NOT_FOUND`, `CARD_INACTIVE`, `TXN_NOT_FOUND`, `LIMIT_EXCEEDED`, `TXN_INVALID_STATE` |

---

## Skipped (Out of Scope / Architectural)

| # | Finding | Reason Skipped |
|---|---------|----------------|
| D | Missing `@Transactional(readOnly=true)` on read methods | Requires systematic review of every controller and service method — low risk but high surface area; no functional bug |
| E | Feign fallback classes never tested for circuit-breaker state transitions | Requires Chaos Monkey or Testcontainers with controlled service failure — recommended as a separate test initiative |
| F | Saga compensation end-to-end integration test | Compensation unit tests (3 paths) already exist in `TransferSagaTest`; full integration test needs Testcontainers + PostgreSQL |

---

## Test Results After All Fixes

| Service | Tests | Result |
|---------|-------|--------|
| accounts-service | 68 | ✅ BUILD SUCCESS |
| transactions-service | 60 | ✅ BUILD SUCCESS |
| fraud-service | 37 | ✅ BUILD SUCCESS |
| loans-service | 53 | ✅ BUILD SUCCESS |

---

## New Grafana Metrics Available

After fix #7, the following metrics are now emitted and can be visualised in Grafana:

| Metric | Tag | Description |
|--------|-----|-------------|
| `scheduler.dormancy.duration` | — | Daily dormancy detection job duration |
| `scheduler.card.expiry.alert.duration` | — | Daily expiry-alert send job duration |
| `scheduler.card.mark.expired.duration` | — | Midnight card-expiry status update duration |
| `scheduler.outbox.duration` | `service=accounts\|loans\|transactions` | Outbox publish poll duration per service |
| `fraud.scoring.duration` | `action=APPROVE\|FLAG\|BLOCK` | Per-transaction fraud scoring latency |

---

## K8s ConfigMap Tuning (Fix #12)

Resilience4j thresholds can now be adjusted per environment without redeployment:

```yaml
# Example K8s ConfigMap / env override
CB_SLIDING_WINDOW: "10"       # accounts, loans
CB_FAILURE_RATE: "50"
CB_WAIT_DURATION: "10s"
CB_TIMEOUT: "5s"

CB_FRAUD_WINDOW: "5"          # transactions → fraud circuit
CB_FRAUD_FAILURE_RATE: "30"
CB_FRAUD_WAIT: "5s"
CB_FRAUD_TIMEOUT: "3s"

CB_ACCOUNTS_WINDOW: "10"      # transactions → accounts circuit
CB_ACCOUNTS_FAILURE_RATE: "50"
CB_ACCOUNTS_WAIT: "10s"
CB_ACCOUNTS_TIMEOUT: "5s"

outbox.publish.interval-ms: "10000"   # outbox poll interval (all services)
```
