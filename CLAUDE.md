# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

**Install BOM first (required once before building services):**
```powershell
cd rewabank-bom && mvn install -q
```

**Build all services (skip tests):**
```powershell
mvn clean install -DskipTests
```

**Build a single service:**
```powershell
cd {service-name} && mvn clean package -DskipTests
```

**Run all tests for a service:**
```powershell
cd {service-name} && mvn test
```

**Run a single test class:**
```powershell
cd {service-name} && mvn test -Dtest=ClassName
```

**Run a single test method:**
```powershell
cd {service-name} && mvn test -Dtest=ClassName#methodName
```

**Run the app locally:**
```powershell
cd {service-name} && mvn spring-boot:run
```

**Docker build:**
```powershell
cd {service-name} && mvn -q -DskipTests package && docker build -t rewabank/{service-name}:latest .
```

## Architecture Overview

**Stack:** Java 21, Spring Boot 3.4.3, Spring Cloud 2024.0.1, deployed on k3d (K3s in Docker) with Istio service mesh.

### Services and Ports

| Service | Port | Role |
|---|---|---|
| api-gateway | 8072 | Reactive Spring Cloud Gateway — all external traffic enters here |
| auth-service | 8073 | Keycloak Admin Client wrapper — registers users in Keycloak realm |
| customers-service | 8080 | KYC + MinIO document storage |
| accounts-service | 8081 | CQRS + Outbox pattern — commands to PostgreSQL, reads from Redis |
| transactions-service | 8082 | Saga pattern — idempotency via Redis, no retries on writes |
| loans-service | 8090 | Loan lifecycle |
| cards-service | 8092 | Card management |
| fraud-service | 8094 | Redis rule engine (<100ms) + async Kafka pattern analysis |
| notifications-service | 9010 | Pure Kafka consumer, MongoDB for templates/logs |
| audit-service | 9011 | Append-only RBI compliance log via Kafka consumer |

### Service Communication

**Synchronous (Feign + Resilience4j):**
- Accounts → Customers (customer validation)
- Transactions → Fraud (real-time check, 3s hard timeout)
- Transactions → Accounts (balance updates)
- Cards → Customers (KYC check)

**Asynchronous (Kafka topics prefixed `bank.*`):**
- `bank.kyc.verified`, `bank.account.created/activated/frozen/closed`, `bank.balance.updated`, `bank.fraud.alert.raised`
- Consumer groups follow `{service-name}-ms` naming convention
- Audit service has no circuit breaker — it must always write

### Security

- OAuth2 Resource Server on every service — JWT validated against Keycloak JWKS endpoint
- API Gateway injects `X-User-Id` header after JWT validation
- Financial operations require `X-Idempotency-Key` header; idempotency state stored in Redis
- Istio mTLS STRICT on the `banking` namespace (zero-trust service-to-service)

### Database Strategy

- Each service has its own PostgreSQL database (CloudNativePG operator in K8s)
- Schema managed by **Flyway** (`classpath:db/migration`) — JPA `ddl-auto: validate` (never create/update)
- Redis/Valkey: CQRS read-side (accounts), idempotency keys (transactions), rule engine (fraud), rate limiter (gateway)
- MongoDB: notifications-service only (templates + delivery logs)

### Dependency Management

`rewabank-bom/pom.xml` is the Bill of Materials — all dependency versions are declared there. Child service POMs import it and must not pin their own versions for managed dependencies. Build the BOM with `mvn install` before building any service.

### Application Configuration

Services default to K8s DNS hostnames (e.g., `valkey.banking.svc.cluster.local`, `kafka-kafka-bootstrap.banking.svc.cluster.local`). For local development without K8s, override these in `application.yml` or via environment variables.

Tests use H2 in-memory database with a separate `application-test.yml` (or `application.yml` in `src/test/resources`). Integration tests use Testcontainers.

### K8s Infrastructure (namespace: `banking`)

- **PostgreSQL:** CloudNativePG (`postgresql-rw.banking.svc.cluster.local:5432`)
- **Cache:** Valkey 8 (`valkey.banking.svc.cluster.local:6379`, password: `redis@2024`)
- **Kafka:** Strimzi 4.1.0 KRaft (`kafka-kafka-bootstrap.banking.svc.cluster.local:9092`)
- **MongoDB:** Community Operator (`mongodb-svc.banking.svc.cluster.local:27017`)
- **Identity:** Keycloak (`keycloak.keycloak.svc.cluster.local`, realm: `rewabank`, client: `rewabank-ms`)
- **Observability:** Prometheus, Grafana (:3000), Jaeger (:16686), Kiali (:20001), OTel Collector

Setup scripts: `k8s/start-cluster.ps1` (cluster + Istio), `k8s/setup-keycloak.ps1` (realm provisioning). Full setup guide: `local-k8s-setup-k3d.md`.

### API Gateway Routing Rules

- Rate limiting: 20 req/s general, 5 req/s for transactions/payments (Redis-backed per `X-User-Id`)
- **No retries** on transactions or payments (idempotency — double-charge risk)
- Loans: GET-only retries (max 2)
- Timeouts: 3s fraud, 4s default, 10–15s financial operations
- Circuit breaker per service via Resilience4j; fallback URIs defined in gateway config

### Docker Image Pattern

All services use the same multi-stage Dockerfile: Maven build → layer extraction → Eclipse Temurin 21 JRE Alpine runtime with a non-root `rewabank` group user. Healthcheck hits `/actuator/health`. JVM is configured to use 75% of container RAM.
