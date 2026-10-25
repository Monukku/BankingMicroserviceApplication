# Resilience Audit — Retry Overlap & Timeout Ladder (report-only)

Date: 2026-10-25. Scope: api-gateway + Istio. **No changes applied — findings + proposed values only.**

## Request path (outer → inner)
```
Client → Istio ingress VS (rewabank-ingress-vs) → Spring Gateway (circuit breaker + time-limiter + httpclient)
       → Istio per-service VS (e.g. accounts-ms-vs) → Istio DestinationRule outlier detection → Service
```

## Current state — retries & timeouts per path

| Path | Ingress VS (outer) | Spring Gateway | Backend Istio VS (inner) |
|---|---|---|---|
| accounts (`/api/v1` catch-all) | 30s, **retries 2** (perTry 10s, retryOn connect-failure,reset,gateway-error) | CB, time-limiter 10s, no retry, httpclient 15s | accounts-ms-vs: 10s, **retries 2** (perTry 5s, retryOn gateway-error,reset) |
| loans (catch-all) | 30s, **retries 2** | CB, TL 10s, **.retry(2) GET** | none |
| transactions | 10s, retries 0 | CB, TL 10s, no retry | none |
| payments | **10s**, retries 0 | CB, TL **15s**, no retry | none |
| fraud (catch-all) | 30s, retries 2 | CB, TL 3s, no retry | none |
| auth / customers / cards / audit (catch-all) | 30s, retries 2 | CB, TL 10–15s | none |
| kyc | 60s, retries 0 | TL 10s | none |

DestinationRule outlier (accounts, api-gateway): `consecutiveGatewayErrors: 5, interval 10s, baseEjectionTime 30s, maxEjectionPercent 50`.

## Rule 1 — retries in ONE layer only → VIOLATED
1. **accounts: double retry across two Istio VirtualServices** — ingress catch-all (2×) nested over `accounts-ms-vs` (2×) → up to (1+2)×(1+2) = **9 hits** on a sick service.
2. **loans: double retry** — ingress catch-all (2×) × Spring gateway `.retry(2)`.
3. transactions / payments: 0 everywhere ✅.

Extra retry risks:
- Ingress catch-all `retryOn: gateway-error` **retries 503s — including the circuit breaker's deliberate 503 fallback** → spurious load on an already-open breaker.
- Catch-all + `accounts-ms-vs` retry **writes** (POST create, PATCH balance) on `gateway-error` → double-apply risk for non-idempotent writes.

## Rule 2 — timeouts shrink inward (outer ≥ inner, with margin) → VIOLATED / tie
1. **payments INVERTED (highest risk — it's a write):** ingress 10s < gateway time-limiter 15s. Client told it failed at 10s while the gateway processes the payment for 5 more seconds.
2. **transactions: no margin** — ingress 10s == gateway TL 10s (race).
3. **accounts: inner retry budget ≈ outer cut** — `accounts-ms-vs` (10s + 2×5s) ≈ gateway TL 10s, and ingress retries every 10s up to 30s → client waits ~30s while each try hammers accounts.
4. **Loose outers:** ingress catch-all 30s, kyc 60s ≫ inner cuts (wasteful, not harmful).
5. **Single replica + outlier:** `baseEjectionTime 30s` with one pod = 30s full blackout on ejection.

## Correct timeout rule
Inside the gateway, **time-limiter ≤ httpclient response-timeout** so the per-route time-limiter is authoritative (fires first → clean per-route fallback); httpclient is a slightly-larger global backstop. Across layers, largest→smallest:
```
ingress  ≥  httpclient  ≥  gateway time-limiter  ≥  backend-VS
```

## Proposed values (not applied)

Columns ordered to match the ladder: **Ingress ≥ httpclient ≥ time-limiter ≥ backend-VS**.

| Path | Ingress | httpclient | Gateway time-limiter | Backend-VS |
|---|---|---|---|---|
| accounts | 13s | 11s | 10s | 8s (perTry 4s) |
| transactions | 12s | 11s | 10s | 8s |
| **payments** | **18s** | 16s | 15s | 12s |
| fraud | 4s | 3.5s | 3s | 2.5s |
| auth | 18s | 16s | 15s | — |

(httpclient `response-timeout` is a single global value — set it to the max row, i.e. ~16s, so it never cuts before any per-route time-limiter.)

## Proposed retry policy — retry in ONE layer, GET-only
- **Ingress catch-all `/api/v1`: `attempts: 0`** (was 2).
- **Backend per-service VS owns retries, GET-only**: `retryOn: connect-failure,reset` (drop `gateway-error` so 503 fallbacks and processed-but-failed writes are never replayed); scope to idempotent GETs.
- **Spring gateway: drop `loans.retry(2)`** (let Istio own it).
- transactions / payments: stay 0 everywhere.
- Net result: **zero retries on any write (POST/PATCH)**, single retry layer for idempotent GETs.

## Suggested apply order (each its own commit + tests)
1. **Payments timeout ladder first** (riskiest — write path inversion).
2. **Accounts + loans retry de-dup** (amplification).
3. (Optional) tighten the loose ingress/kyc timeouts and reconsider `baseEjectionTime` for single-replica services.