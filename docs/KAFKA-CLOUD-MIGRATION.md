# Kafka → Managed Cloud Migration Runbook

Move Kafka out of the k3d cluster to a managed broker (Aiven / Confluent Cloud /
Redpanda Cloud) to free cluster resources. **Valkey/Redis stays in-cluster.**

## How the app side works (already done)
Every Kafka service applies transport security **conditionally**:
- Default = `PLAINTEXT` → unchanged in-cluster / dev / test behavior.
- Set `KAFKA_SECURITY_PROTOCOL` to `SASL_SSL` (+ the vars below) → the producer/
  consumer clients switch to authenticated TLS. No code change needed to flip.

### Env contract (set per service, from a K8s Secret)
| Env var | Example | Notes |
|---|---|---|
| `KAFKA_BOOTSTRAP` | `kafka-xxxx.aivencloud.com:12345` | all services except payments |
| `KAFKA_BOOTSTRAP_SERVERS` | same value | **payments-service** uses this name |
| `KAFKA_SECURITY_PROTOCOL` | `SASL_SSL` | the on/off switch |
| `KAFKA_SASL_MECHANISM` | `SCRAM-SHA-512` | or `PLAIN` (Confluent), per provider |
| `KAFKA_SASL_JAAS_CONFIG` | `org.apache.kafka.common.security.scram.ScramLoginModule required username="USER" password="PASS";` | SCRAM login module |
| `KAFKA_SSL_TRUSTSTORE_LOCATION` | `/etc/kafka/truststore.p12` | **only** if provider uses a private CA (Aiven). Omit for publicly-trusted CAs (Confluent/Redpanda). |
| `KAFKA_SSL_TRUSTSTORE_PASSWORD` | `changeit` | with the above |
| `KAFKA_SSL_TRUSTSTORE_TYPE` | `PKCS12` | default already PKCS12 |

> For `PLAIN` mechanism the JAAS module is `org.apache.kafka.common.security.plain.PlainLoginModule`.

---

## CLOUD-SIDE WORK — Aiven (step by step)

> Cost note: Aiven Kafka has **no permanent free tier** (cheapest ~Startup-2 ≈ $200/mo;
> 1-month trial ≈ $300 credits). Redpanda Cloud / Confluent Cloud have free tiers and
> the app env contract is identical (Confluent uses `PLAIN` instead of `SCRAM-SHA-512`).

1. **Create the service:** console.aiven.io → **Create service** → **Apache Kafka** →
   pick cloud + **region nearest your machine** → plan **Startup-2** → name
   `rewabank-kafka` → **Create**. Wait for status **Running** (~3–5 min).
2. **Enable SASL** (Aiven defaults to mTLS/client-cert; our app uses SASL/SCRAM):
   service → **Service settings** → **Advanced configuration** → add
   `kafka_authentication_methods.sasl` = **Enabled** → **Save**. This exposes a
   separate **SASL port** (different from the default mTLS port — use the SASL one).
3. **Connection info:** Overview → **Connection information** → Authentication Method
   dropdown = **SASL**. Copy **Host**, **Port (SASL)**, **User** (`avnadmin`),
   **Password**. Click **Download** on the **CA certificate** → `ca.pem`.
4. **Topics:** Topics tab → add each topic (partitions 3, replication 3) from the list
   below, **or** for a quick demo set Advanced config
   `kafka.auto_create_topics_enable` = **Enabled**.
5. **(optional) least-privilege user:** `avnadmin` is superuser (fine for a demo). For
   least privilege, Users tab → add user; ACL tab → grant read/write on `bank.*` and
   read on the `*-ms` consumer groups.
6. **Build the truststore** from the downloaded CA:
   ```bash
   keytool -import -alias aiven-ca -file ca.pem \
     -keystore truststore.p12 -storetype PKCS12 -storepass changeit -noprompt
   ```

### Map Aiven values → env vars
| Aiven value | Env var |
|---|---|
| `Host:SASL-Port` | `KAFKA_BOOTSTRAP` (+ `KAFKA_BOOTSTRAP_SERVERS` for payments) |
| — | `KAFKA_SECURITY_PROTOCOL=SASL_SSL` |
| — | `KAFKA_SASL_MECHANISM=SCRAM-SHA-512` |
| User + Password | `KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.scram.ScramLoginModule required username="avnadmin" password="<PASSWORD>";` |
| `truststore.p12` | `KAFKA_SSL_TRUSTSTORE_LOCATION=/etc/kafka/truststore.p12` + `KAFKA_SSL_TRUSTSTORE_PASSWORD=changeit` |

### Other providers (identical app side)
- **Confluent Cloud / Redpanda Cloud** (public CA → no truststore needed): omit the
  `KAFKA_SSL_TRUSTSTORE_*` vars. Confluent uses `KAFKA_SASL_MECHANISM=PLAIN` with
  `org.apache.kafka.common.security.plain.PlainLoginModule` + API key/secret.

### Topics to create
Core (from the service configs):
```
bank.user.registered           bank.user.locked            bank.kyc.verified
bank.customer.updated           bank.account.created        bank.account.activated
bank.account.frozen             bank.account.closed         bank.account.dormant
bank.balance.updated            bank.transaction.initiated  bank.transaction.completed
bank.transaction.failed         bank.transaction.reversed   bank.fraud.alert.raised
bank.fraud.alert.cleared        bank.loan.applied           bank.loan.approved
bank.loan.rejected              bank.loan.disbursed         bank.repayment.completed
bank.repayment.overdue          bank.card.issued            bank.card.blocked
bank.payment.success            bank.payment.failed
```
Plus `@RetryableTopic` (notifications) auto-derives **`<topic>.retry`** and
**`<topic>.dlq`** per consumed topic — either enable auto-create for those or
pre-create them.

---

## CLUSTER-SIDE WORK

6. **K8s Secret** (namespace `banking`):
```bash
kubectl -n banking create secret generic kafka-cloud \
  --from-literal=KAFKA_BOOTSTRAP='kafka-xxxx.aivencloud.com:12345' \
  --from-literal=KAFKA_SECURITY_PROTOCOL='SASL_SSL' \
  --from-literal=KAFKA_SASL_MECHANISM='SCRAM-SHA-512' \
  --from-literal=KAFKA_SASL_JAAS_CONFIG='org.apache.kafka.common.security.scram.ScramLoginModule required username="USER" password="PASS";' \
  --from-file=truststore.p12=./truststore.p12 \
  --from-literal=KAFKA_SSL_TRUSTSTORE_LOCATION='/etc/kafka/truststore.p12' \
  --from-literal=KAFKA_SSL_TRUSTSTORE_PASSWORD='changeit'
```
7. **Wire each service Deployment** — envFrom the secret (and `KAFKA_BOOTSTRAP_SERVERS`
   for payments), and mount the truststore if using a private CA:
```yaml
envFrom:
  - secretRef: { name: kafka-cloud }
env:
  - name: KAFKA_BOOTSTRAP_SERVERS     # payments-service only
    valueFrom: { secretKeyRef: { name: kafka-cloud, key: KAFKA_BOOTSTRAP } }
volumeMounts:
  - { name: kafka-truststore, mountPath: /etc/kafka, readOnly: true }
volumes:
  - name: kafka-truststore
    secret: { secretName: kafka-cloud, items: [{ key: truststore.p12, path: truststore.p12 }] }
```
8. **Istio egress** (namespace is mTLS STRICT, so external egress needs this):
```yaml
apiVersion: networking.istio.io/v1
kind: ServiceEntry
metadata: { name: kafka-cloud, namespace: banking }
spec:
  hosts: ["kafka-xxxx.aivencloud.com"]
  ports: [{ number: 12345, name: tcp-kafka, protocol: TCP }]
  resolution: DNS
  location: MESH_EXTERNAL
---
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata: { name: kafka-cloud, namespace: banking }
spec:
  host: kafka-xxxx.aivencloud.com
  trafficPolicy: { tls: { mode: DISABLE } }   # the Kafka client does its own TLS
```
9. **Roll out & verify:** `kubectl rollout restart deploy -n banking`; check each pod
   logs for a successful broker connection + consumer-group join (no SASL/SSL errors).
10. **Decommission in-cluster Kafka** once stable: remove the Strimzi `Kafka` CR and
    `KafkaTopic` CRs (and drop Kafka from `k8s/start-cluster.ps1`). **Leave Valkey.**

---

## Cutover & rollback
- **Cutover** is just setting the env (step 6–7) + restart. No image rebuild.
- **Rollback**: delete/clear `KAFKA_SECURITY_PROTOCOL` (→ PLAINTEXT) and point
  `KAFKA_BOOTSTRAP` back to `kafka-kafka-bootstrap.banking.svc.cluster.local:9092`,
  restart. In-flight events are buffered by whichever broker is active.

## Gotchas
- **Auto-create off** on managed brokers → pre-create the `.retry`/`.dlq` topics.
- **Cost:** Aiven Kafka is a paid plan; Confluent/Redpanda have free tiers/credits.
- **Offsets** start fresh on the new broker (`auto-offset-reset=earliest`) — expect a
  one-time replay of retained events on first connect.
- **Latency** is async-path only (safe); producers already use `acks=all` + idempotence.