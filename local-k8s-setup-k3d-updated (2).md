# RewaBank — Local K8s + Istio Setup
# Docker desktop crash sol run  "wsl --shutdown" instead restarting system
## k3d on Windows 16GB RAM (Lightweight — No Docker Desktop K8s)

## Overview

```
Step 1  — Docker Desktop (engine only) + k3d cluster
Step 2  — Install istioctl on Windows
Step 3  — Install Istio (demo profile)
Step 4  — Namespaces + sidecar injection
Step 5  — Helm repos
Step 6  — Deploy infra (PostgreSQL, Valkey, Kafka, MongoDB, Backblaze B2, OTel, Keycloak)
Step 7  — K8s Secrets
Step 8  — Build Docker images locally
Step 9  — Deploy all MS
Step 10 — Istio Ingress Gateway (permanent access — no port-forward)
Step 11 — Configure Keycloak realm
Step 12 — Verify + test
Step 13 — Observability Dashboards
Step 14 — Angular setup
Step 15 — Performance / Load testing
```

> **No port-forward needed anywhere in this guide.**
> All services are accessible via Istio Ingress at `http://localhost`.

> **RAM note (16GB machine):** All deployments are set to `replicas: 1` and HPA `maxReplicas: 1`.
> cards-service and loans-service are excluded from this local setup.
> Scale down Istio addons when not in use — see RAM Saving Tips at the bottom.

---

# STEP 1 — Docker Desktop + k3d Setup

## Why k3d instead of Docker Desktop Kubernetes?
```
Docker Desktop K8s  → 2-3GB RAM, slow start (3-5 min), hard to reset
k3d                 → ~512MB RAM, starts in 30 sec, instant reset
k3d runs k3s (lightweight K8s) inside Docker containers
Docker Desktop is still needed as the container engine — just disable its built-in K8s
```

## 1.1 Install Docker Desktop
```
Download: https://www.docker.com/products/docker-desktop/
Install with default settings
Restart Windows after install
```

## 1.2 Configure Docker Desktop (engine only — disable built-in K8s)
```
Docker Desktop
→ Settings (gear icon top right)
→ Resources
→ Memory: 10GB        ← set this
→ CPUs:   4
→ Swap:   2GB
→ Disk:   80GB
→ Apply & Restart

Then:
→ Settings
→ Kubernetes
→ ❌ DISABLE Enable Kubernetes   ← turn this OFF (we use k3d instead)
→ Apply & Restart
```

## 1.3 Configure WSL2 memory limit
```powershell
# Open PowerShell as Administrator
@"
[wsl2]
memory=10GB
processors=4
swap=2GB
"@ | Set-Content C:\Users\$env:USERNAME\.wslconfig

wsl --shutdown
# Restart Docker Desktop after wsl shutdown
```

## 1.4 Install k3d
```powershell
# Option A — winget (easiest)
winget install k3d

# Option B — chocolatey
choco install k3d

# Verify
k3d version
```

## 1.5 Install kubectl
```powershell
winget install Kubernetes.kubectl

# Verify
kubectl version --client
```

## 1.6 Create k3d cluster
```powershell
k3d cluster create rewabank `
  --agents 1 `
  --port "80:80@loadbalancer" `
  --port "443:443@loadbalancer" `
  --k3s-arg "--disable=traefik@server:0" `
  --k3s-arg "--disable=servicelb@server:0"

kubectl get nodes
# Expected:
# k3d-rewabank-server-0   Ready   control-plane
# k3d-rewabank-agent-0    Ready   <none>
```

## 1.7 Useful k3d cluster management commands
```powershell
# Stop cluster (saves RAM)
k3d cluster stop rewabank

# ⚠️ IMPORTANT: Always use the startup script when starting — not k3d directly
# See: k8s/start-cluster.ps1

# Delete and recreate cluster
k3d cluster delete rewabank
k3d cluster create rewabank --agents 1 --port "80:80@loadbalancer" --port "443:443@loadbalancer" --k3s-arg "--disable=traefik@server:0" --k3s-arg "--disable=servicelb@server:0"

# List clusters
k3d cluster list
```

## 1.8 Cloudflare Tunnel (external access via api.rewarealm.com)

> The Cloudflare Tunnel exposes the local cluster to the internet via `https://api.rewarealm.com`.
> All external traffic (Angular app, mobile, API testing) goes through this tunnel.
> It must be running whenever you want external access — keep it open in a separate terminal.

```
Tunnel ID:  6726812b-0ba7-4f6a-bc83-1e2dfc0f8113
Domain:     api.rewarealm.com
Credential: C:\Users\Monukushw\.cloudflared\6726812b-0ba7-4f6a-bc83-1e2dfc0f8113.json
```

```powershell
# Run in a SEPARATE terminal — keep this running at all times
cloudflared tunnel run rewabank
```

```
Expected output:
  Registered tunnel connection connIndex=0 ... location=bom09 protocol=quic
  Registered tunnel connection connIndex=1 ... location=bom06 protocol=quic
  Registered tunnel connection connIndex=2 ... location=bom06 protocol=quic
  Registered tunnel connection connIndex=3 ... location=bom08 protocol=quic
  (4 connections to Cloudflare Mumbai PoP — all healthy)

⚠️ ERR "Incoming request ended abruptly: context canceled" errors are EXPECTED
   until Istio ingress + all microservices are fully deployed (Steps 9-10).
   These errors stop once the cluster is fully up.

⚠️ ERR "Failed to refresh DNS local resolver" is a temporary network hiccup — not critical.
```

```powershell
# Install cloudflared (if not already installed)
winget install Cloudflare.cloudflared

# Verify
cloudflared version
```

---

# STEP 2 — Install istioctl on Windows

```powershell
# Option A — winget
winget install Istio.istioctl

# Verify
istioctl version
```

---

# STEP 3 — Install Istio

```powershell
istioctl install --set profile=demo -y

# Wait 3-5 minutes then verify
kubectl get pods -n istio-system
# Expected: istiod, istio-ingressgateway, istio-egressgateway all Running

# Install observability addons — use the version that matches your istioctl
$ISTIO_VERSION = (istioctl version --remote=false 2>&1 | Select-String "client version" | ForEach-Object { ($_ -replace ".*client version: ", "").Trim().Substring(0,4) })
Write-Host "Installing Istio addons for version: $ISTIO_VERSION"

kubectl apply -f "https://raw.githubusercontent.com/istio/istio/release-$ISTIO_VERSION/samples/addons/kiali.yaml"
kubectl apply -f "https://raw.githubusercontent.com/istio/istio/release-$ISTIO_VERSION/samples/addons/prometheus.yaml"
kubectl apply -f "https://raw.githubusercontent.com/istio/istio/release-$ISTIO_VERSION/samples/addons/grafana.yaml"
kubectl apply -f "https://raw.githubusercontent.com/istio/istio/release-$ISTIO_VERSION/samples/addons/jaeger.yaml"

kubectl wait --for=condition=ready pod -l app=kiali   -n istio-system --timeout=120s
kubectl wait --for=condition=ready pod -l app=grafana -n istio-system --timeout=120s

echo "Istio ready"
```

---

# STEP 4 — Namespaces + Sidecar Injection

```powershell
kubectl create namespace banking
kubectl create namespace keycloak
kubectl create namespace monitoring

# Enable Istio sidecar injection
# ⚠️ IMPORTANT: keycloak namespace MUST have istio-injection=enabled
# otherwise Keycloak cannot connect to PostgreSQL (mTLS STRICT blocks it)
kubectl label namespace banking istio-injection=enabled
kubectl label namespace keycloak istio-injection=enabled

# Verify
kubectl get namespace banking --show-labels
kubectl get namespace keycloak --show-labels
# Both should show: istio-injection=enabled

# Apply mTLS STRICT
kubectl apply -f C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025\k8s\mtls-strict.yaml

echo "Namespaces ready"
```

---

# STEP 5 — Add Helm Repos

```powershell
winget install Helm.Helm

helm repo remove bitnami 2>$null

# cnpg (CloudNativePG) removed — PostgreSQL now hosted on Neon cloud (see Step 6.1)
helm repo add strimzi     https://strimzi.io/charts/
helm repo add mongodb     https://mongodb.github.io/helm-charts
helm repo add codecentric https://codecentric.github.io/helm-charts

helm repo update
helm repo list
```

---

# STEP 6 — Deploy Infrastructure

## 6.1 PostgreSQL — Neon Cloud (replaces CloudNativePG)

> **All relational databases are hosted on Neon (neon.tech) — no local PostgreSQL in the cluster.**
> This frees ~450 MB RAM vs running CloudNativePG locally.
> Neon free tier: 0.5 GB storage, 20 max connections. All 8 DBs use ~40 MB total.
>
> ⚠️ Neon auto-suspends compute after 5 min idle. First query after suspension adds 1–3s latency.
> ⚠️ HikariCP pool size is set to 2 per service to stay within the 20 connection limit.

### 6.1.1 Neon project setup (one-time)

```
1. Go to https://neon.tech → sign up with GitHub (free, no credit card)
2. Create project:
   Name:   rewabank
   Region: AWS ap-south-1 (Mumbai) — closest for India
3. Note your connection host: <something>.ap-south-1.aws.neon.tech
```

### 6.1.2 Create all databases

Run this in the **Neon SQL Editor** (dashboard → SQL Editor → connected to default database):

```sql
CREATE DATABASE auth_db;
CREATE DATABASE customers_db;
CREATE DATABASE accounts_db;
CREATE DATABASE transactions_db;
CREATE DATABASE fraud_db;
CREATE DATABASE audit_db;
CREATE DATABASE keycloak_db;
CREATE DATABASE payments_db;
```

### 6.1.3 Create per-service roles with proper isolation

> ⚠️ **Always create roles via SQL, never via the Neon Console.**
> Console-created roles become `neon_superuser` (admin access to all databases).
> SQL-created roles with `LOGIN` only have the privileges you explicitly grant.

> ⚠️ **Do not use `CREATE USER ... WITH PASSWORD 'hardcoded'`.**
> Use a strong random password and store it only in the K8s secret.
> Get the password from: Neon dashboard → Roles → click user → Reset password.

**Step A — Create roles (run in SQL Editor, connected to any database):**

```sql
-- No admin rights — LOGIN only
CREATE ROLE accounts_user     LOGIN PASSWORD '<strong-random-password>';
CREATE ROLE auth_user         LOGIN PASSWORD '<strong-random-password>';
CREATE ROLE customers_user    LOGIN PASSWORD '<strong-random-password>';
CREATE ROLE fraud_user        LOGIN PASSWORD '<strong-random-password>';
CREATE ROLE payments_user     LOGIN PASSWORD '<strong-random-password>';
CREATE ROLE transactions_user LOGIN PASSWORD '<strong-random-password>';
CREATE ROLE keycloak_user     LOGIN PASSWORD '<strong-random-password>';

-- Allow neondb_owner to act as these roles (required for ownership transfers)
GRANT accounts_user, auth_user, customers_user, fraud_user,
      payments_user, transactions_user, keycloak_user TO neondb_owner;
```

**Step B — Transfer database ownership and block cross-database access:**

```sql
ALTER DATABASE accounts_db     OWNER TO accounts_user;
ALTER DATABASE auth_db         OWNER TO auth_user;
ALTER DATABASE customers_db    OWNER TO customers_user;
ALTER DATABASE fraud_db        OWNER TO fraud_user;
ALTER DATABASE payments_db     OWNER TO payments_user;
ALTER DATABASE transactions_db OWNER TO transactions_user;
ALTER DATABASE keycloak_db     OWNER TO keycloak_user;
-- audit_db stays owned by neondb_owner (audit-service uses neondb_owner)

-- Block all roles from connecting to databases they don't own
REVOKE CONNECT ON DATABASE accounts_db, auth_db, customers_db, fraud_db,
       payments_db, transactions_db, keycloak_db FROM PUBLIC;
```

**Step C — Reset public schema in each database (run per database via dropdown):**

> Switch to each database in the SQL Editor dropdown, then run:

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
-- Verify: schema_owner should match the database owner
SELECT current_user, nspowner::regrole AS schema_owner
FROM pg_namespace WHERE nspname = 'public';
```

> ⚠️ This wipes all existing tables. Flyway recreates them on next service startup.
> Do this on a fresh setup or when re-provisioning. Never on a live database with data.

**Step D — Verify no service role has admin rights:**

```sql
SELECT rolname FROM pg_roles r
WHERE rolname LIKE '%\_user'
  AND pg_has_role(r.oid, 'neon_superuser', 'MEMBER');
-- Expected: 0 rows
```

### 6.1.4 Connection string format

Neon requires SSL. All service datasource URLs follow this pattern:
```
jdbc:postgresql://<neon-host>:5432/<db_name>?sslmode=require
```

Example:
```
jdbc:postgresql://ep-xxx.ap-southeast-1.aws.neon.tech:5432/accounts_db?sslmode=require
```

### 6.1.5 Neon database summary

| Database | Service | Role | Admin rights | K8s Secret |
|---|---|---|---|---|
| auth_db | auth-service | auth_user | ❌ none | auth-db-secret |
| customers_db | customers-service | customers_user | ❌ none | customers-db-secret |
| accounts_db | accounts-service | accounts_user | ❌ none | accounts-db-secret |
| transactions_db | transactions-service | transactions_user | ❌ none | transactions-db-secret |
| fraud_db | fraud-service | fraud_user | ❌ none | fraud-db-secret |
| audit_db | audit-service | neondb_owner | owner | audit-db-secret |
| keycloak_db | Keycloak | keycloak_user | ❌ none | keycloak-db-secret (keycloak ns) |
| payments_db | payments-service | payments_user | ❌ none | payments-db-secret |

> **Passwords are never stored in this file.**
> Get them from: Neon dashboard → Roles → click role → Reset password.
> Store them only in K8s Secrets (see Step 7).

```
echo "Neon PostgreSQL configured — no local PostgreSQL needed"
```

## 6.2 Valkey (Redis)

```powershell
kubectl apply -f C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025\k8s\valkey.yaml
kubectl wait --for=condition=ready pod -l app=valkey -n banking --timeout=180s
echo "Valkey ready"
# Use: valkey.banking.svc.cluster.local:6379  password: redis@2024
```

## 6.3 Kafka (Strimzi)

```powershell
helm install strimzi strimzi/strimzi-kafka-operator --namespace banking --set watchNamespaces="{banking}"
kubectl wait --for=condition=ready pod -l name=strimzi-cluster-operator -n banking --timeout=120s

kubectl apply -f C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025\k8s\kafka-cluster.yaml
kubectl wait kafka/kafka --for=condition=Ready --timeout=300s -n banking

echo "Kafka ready"
# Use: kafka-kafka-bootstrap.banking.svc.cluster.local:9092
```

## 6.4 MongoDB Atlas (replaces in-cluster MongoDB)

> **audit-service and notifications-service use MongoDB Atlas — no local MongoDB in the cluster.**
> This frees ~300 MB RAM vs running MongoDB Community Operator locally.
> Each service gets its own Atlas database and connection URI stored in a K8s Secret.

```
Cluster:  cluster0.rcejz7c.mongodb.net  (M0 free tier — AWS ap-south-1)
User:     rewabank_user  (readWrite to both databases)
Databases:
  notifications_db  → collections: notification_templates, delivery_logs
  audit_db          → collections: audit_log
```

### 6.4.1 Atlas setup (one-time)

**1. Create account & cluster**
```
1. Go to https://cloud.mongodb.com → sign up / log in
2. Click "Build a Database" → choose M0 FREE
3. Region: AWS / ap-south-1 (Mumbai)
4. Cluster name: rewabank → click Create
5. Wait for cluster status to show Active (green dot) — takes 1–3 min
```

**2. Create database user**
```
Left sidebar → Database Access → Add New Database User
  Auth method:   Password
  Username:      rewabank_user
  Password:      <generate strong password — store it only in K8s Secret>
  Built-in role: Read and write to any database
→ Add User
```

**3. Whitelist network access**
```
Left sidebar → Network Access → Add IP Address
  → Allow Access from Anywhere (0.0.0.0/0)
  → Confirm
⚠️ Required — k3d pods have dynamic IPs inside WSL2
```

**4. Create databases and collections**
```
Left sidebar → Database → click cluster name → Collections tab
→ Add My Own Data:
  Database: notifications_db   Collection: notification_templates  → Create
  Click "+" next to notifications_db → add collection: delivery_logs

→ Click "+" at top of left panel (next to Collections heading):
  Database: audit_db   Collection: audit_log  → Create
```

**5. Get connection string**
```
Left sidebar → Database → Connect → Drivers → Java → 5.x or later
Copy URI:
  mongodb+srv://rewabank_user:<password>@cluster0.rcejz7c.mongodb.net/?retryWrites=true&w=majority&appName=rewabank
```

### 6.4.2 Create the Atlas secret

> ⚠️ Password is never stored in this file — get it from Atlas dashboard → Database Access → rewabank_user → Edit → reset if needed.
> The full URI (including password) is stored in the K8s Secret — same pattern as other services.
> Use `--dry-run=client -o yaml | kubectl apply -f -` to update an existing secret without errors.

```powershell
# Delete old secret if it exists (e.g. wrong cluster hostname)
kubectl delete secret mongodb-atlas-secret -n banking 2>$null

# Create new secret — replace <db_password> with Atlas password (type directly in terminal)
kubectl create secret generic mongodb-atlas-secret --namespace banking `
  --from-literal=notifications-uri="mongodb+srv://rewabank_user:<db_password>@cluster0.rcejz7c.mongodb.net/notifications_db?retryWrites=true&w=majority&appName=rewabank" `
  --from-literal=audit-uri="mongodb+srv://rewabank_user:<db_password>@cluster0.rcejz7c.mongodb.net/audit_db?retryWrites=true&w=majority&appName=rewabank"

kubectl get secret mongodb-atlas-secret -n banking
echo "MongoDB Atlas secret created — no local MongoDB pod needed"
```

> ⚠️ If notifications-ms or audit-ms crashes with `DNS name not found`:
> 1. Check Atlas dashboard → cluster is Active (not Paused)
> 2. Check Network Access → 0.0.0.0/0 is whitelisted
> 3. Verify secret has correct cluster hostname:
>    `kubectl get secret mongodb-atlas-secret -n banking -o yaml`
> 4. Restart: `kubectl rollout restart deployment/notifications-ms deployment/audit-ms -n banking`

## 6.5 Backblaze B2 (S3-compatible document storage)

> Used by **customers-service** for KYC document uploads.
> Backblaze B2 is S3-compatible — no local deployment needed. Create a bucket and application key in the Backblaze console, then supply the credentials via K8s Secret.

```
Backblaze B2 setup (one-time — done in Backblaze console):
1. Create a bucket:   rewabank-backendDoc   (private, region: eu-central-003)
2. Create an App Key: Read+Write access to that bucket
3. Note down:
   - keyID      → this is your S3 access key
   - applicationKey → this is your S3 secret key

Endpoint: https://s3.eu-central-003.backblazeb2.com
Region:   eu-central-003
Bucket:   rewabank-backendDoc
```

```powershell
# Create b2-secret (replace values with your actual Backblaze key credentials)
kubectl create secret generic b2-secret --namespace banking `
  --from-literal=access-key=<YOUR_BACKBLAZE_KEY_ID> `
  --from-literal=secret-key=<YOUR_BACKBLAZE_APPLICATION_KEY>

echo "Backblaze B2 secret created"
# customers-service reads: S3_ACCESS_KEY (b2-secret.access-key), S3_SECRET_KEY (b2-secret.secret-key)
# S3_ENDPOINT, S3_BUCKET_NAME, S3_REGION are set in customers-ms-config ConfigMap
```

## 6.6 OTel Collector

```powershell
# Deploy OTel collector — all services send traces here
kubectl apply -f C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025\k8s\otel-collector.yaml
kubectl wait --for=condition=ready pod -l app=otel-collector -n monitoring --timeout=120s
echo "OTel Collector ready"
# Receives traces at: otel-collector.monitoring.svc.cluster.local:4318
```

## 6.7 Keycloak

> ⚠️ **keycloak namespace MUST have `istio-injection=enabled`** (done in Step 4).
> The banking namespace uses mTLS STRICT — without an Istio sidecar on the Keycloak pod,
> PostgreSQL rejects the connection with a TLS handshake reset.

> ⚠️ **Keycloak DB is on Neon — use `keycloak_user` credentials** (see Step 7 for secret creation).
> `keycloak-db-secret` must have `username`, `password`, and `db-url` keys.
> Admin credentials are stored in `keycloak-admin-secret` — never hardcoded in keycloak-values.yaml.

> ⚠️ **Probe note:** `sidecar.istio.io/rewriteAppHTTPProbers: "false"` is set in keycloak-values.yaml.
> This is required because Keycloak 26.x uses `KC_HTTP_RELATIVE_PATH=/auth` which applies to the
> management port (9000) too. Without this annotation, Istio strips the `/auth` prefix from probe
> paths causing the pod to stay `1/2` forever. Health endpoints: `/auth/health/live`, `/auth/health/ready`.

```powershell
# Install Keycloak (secrets must exist first — see Step 7)
helm install keycloak codecentric/keycloakx --namespace keycloak `
  --values C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025\k8s\keycloak-values.yaml

# Wait for 2/2 — takes ~2 minutes
kubectl get pods -n keycloak -w
# Expected: keycloak-keycloakx-0   2/2   Running

echo "Keycloak ready"
# Internal DNS: keycloak-keycloakx-http.keycloak.svc.cluster.local:80
# External URL (after Step 10): http://localhost/auth/admin
```

> ⚠️ **If Keycloak pod shows `1/2` forever:** Check `sidecar.istio.io/rewriteAppHTTPProbers: "false"`
> is in keycloak-values.yaml under `podAnnotations`. Then run:
> `helm upgrade keycloak codecentric/keycloakx -n keycloak --values k8s\keycloak-values.yaml`
> `kubectl delete pod keycloak-keycloakx-0 -n keycloak`

> ⚠️ **If Keycloak fails with `idx_login_failure_expire already exists`:**
> This is a Keycloak 26.x migration bug. Fix: go to Neon SQL Editor → select `keycloak_db` →
> `DROP INDEX IF EXISTS public.idx_login_failure_expire;` → then delete the pod.

> ⚠️ **After any keycloak_db schema wipe:** Keycloak recreates schema on next start (Liquibase).
> All realm data (users, clients, roles) is lost. Re-run `setup-keycloak.ps1` after Keycloak is `2/2`.
> `start-cluster.ps1` does this automatically.

---

# STEP 7 — K8s Secrets

> ⚠️ **Security rules — no passwords in YAML files or application.yml**
> All credentials live only in K8s Secrets, injected as env vars at runtime.
> Use `--dry-run=client -o yaml | kubectl apply -f -` to update existing secrets without errors.

> **Neon roles:** Each service has its own Neon role with its own password (least privilege).
> Go to Neon dashboard → Roles → click each user → Reset password to get the password.
> `neondb_owner` owns all databases but each service role only has GRANT access to its own DB.

```powershell
# ── Neon PostgreSQL — per-service secrets (principle of least privilege) ──────
# Get passwords from: Neon dashboard → Roles → click user → Reset password
# Each user only has access to their own database (GRANT set via SQL)

kubectl create secret generic auth-db-secret -n banking `
  --from-literal=username=auth_user `
  --from-literal=password=<AUTH_USER_PASSWORD_FROM_NEON>

kubectl create secret generic customers-db-secret -n banking `
  --from-literal=username=customers_user `
  --from-literal=password=<CUSTOMERS_USER_PASSWORD_FROM_NEON>

kubectl create secret generic accounts-db-secret -n banking `
  --from-literal=username=accounts_user `
  --from-literal=password=<ACCOUNTS_USER_PASSWORD_FROM_NEON>

kubectl create secret generic transactions-db-secret -n banking `
  --from-literal=username=transactions_user `
  --from-literal=password=<TRANSACTIONS_USER_PASSWORD_FROM_NEON>

kubectl create secret generic fraud-db-secret -n banking `
  --from-literal=username=fraud_user `
  --from-literal=password=<FRAUD_USER_PASSWORD_FROM_NEON>

kubectl create secret generic payments-db-secret -n banking `
  --from-literal=username=payments_user `
  --from-literal=password=<PAYMENTS_USER_PASSWORD_FROM_NEON>

kubectl create secret generic audit-db-secret -n banking `
  --from-literal=username=neondb_owner `
  --from-literal=password=<NEONDB_OWNER_PASSWORD_FROM_NEON>

# ── Keycloak DB secret (keycloak namespace) ───────────────────────────────────
# keycloak_db is also on Neon — uses neondb_owner
kubectl create secret generic keycloak-db-secret -n keycloak `
  --from-literal=username=neondb_owner `
  --from-literal=password=<NEONDB_OWNER_PASSWORD_FROM_NEON> `
  --from-literal=db-url="jdbc:postgresql://ep-spring-bar-az9ane6a-pooler.c-3.ap-southeast-1.aws.neon.tech:5432/keycloak_db?sslmode=require"

# ── Keycloak admin credentials ────────────────────────────────────────────────
# Never hardcode admin password in keycloak-values.yaml
kubectl create secret generic keycloak-admin-secret -n keycloak `
  --from-literal=username=admin `
  --from-literal=password=<YOUR_KEYCLOAK_ADMIN_PASSWORD>

# ── Valkey (Redis) ────────────────────────────────────────────────────────────
kubectl create secret generic redis-secret -n banking --from-literal=password=redis@2024

# ── Keycloak MS client (used by all microservices) ────────────────────────────
kubectl create secret generic keycloak-ms-secret -n banking `
  --from-literal=client-id=rewabank-ms `
  --from-literal=client-secret=rewabank-ms-secret-2024

# ── Internal service-to-service call secret ───────────────────────────────────
# Generate a strong random value — used by accounts, transactions, loans services
$internalSecret = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
kubectl create secret generic internal-call-secret -n banking `
  --from-literal=secret=$internalSecret

# ── MongoDB Atlas — separate URI per service ──────────────────────────────────
# Get connection strings from: Atlas dashboard → Connect → Drivers
kubectl create secret generic mongodb-atlas-secret -n banking `
  --from-literal=notifications-uri="mongodb+srv://<user>:<password>@<cluster>.mongodb.net/notifications_db?retryWrites=true&w=majority" `
  --from-literal=audit-uri="mongodb+srv://<user>:<password>@<cluster>.mongodb.net/audit_db?retryWrites=true&w=majority"

# ── AES-256 encryption key (randomly generated) ───────────────────────────────
$aesKey = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
kubectl create secret generic encryption-secret -n banking --from-literal=aes-key=$aesKey

# ── Backblaze B2 (S3-compatible — customers-service KYC documents) ────────────
kubectl create secret generic b2-secret -n banking `
  --from-literal=access-key=<YOUR_BACKBLAZE_KEY_ID> `
  --from-literal=secret-key=<YOUR_BACKBLAZE_APPLICATION_KEY>

# ── Razorpay (payments-service) ───────────────────────────────────────────────
kubectl create secret generic razorpay-secret -n banking `
  --from-literal=key-id=<YOUR_RAZORPAY_KEY_ID> `
  --from-literal=key-secret=<YOUR_RAZORPAY_KEY_SECRET> `
  --from-literal=webhook-secret=<YOUR_RAZORPAY_WEBHOOK_SECRET>

echo "All secrets created"
kubectl get secrets -n banking
kubectl get secrets -n keycloak
```

---

# STEP 8 — Build Docker Images

> ⚠️ Build order matters — BOM must be installed first.
> cards-service and loans-service are excluded (RAM constraint on 16GB machine).

> ⚠️ **CRITICAL — Stop the cluster before every docker build.**
> The k3d cluster + Istio consumes ~4-5GB RAM. Running `docker build` on top of this
> causes WSL2 to run out of memory mid-build, crashing Docker Desktop and corrupting
> the Docker data disk (requires full Windows restart to recover).
> Always stop the cluster first, build the image, then restart the cluster.

```powershell
# Before building ANY image — stop cluster first
k3d cluster stop rewabank

# After ALL images are built — restart cluster
.\k8s\start-cluster.ps1
```

```powershell
$root = "C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025"

# Install BOM first (required once — child POMs import versions from here)
Set-Location "$root\rewabank-bom"
mvn install -q
Set-Location $root

# API Gateway
Set-Location "$root\api-gateway"
mvn -q -DskipTests package
docker build -t rewabank/api-gateway:latest .
docker push rewabank/api-gateway:latest
docker rmi rewabank/api-gateway:latest
docker builder prune -f
Set-Location $root

# Auth MS
Set-Location "$root\auth-service"
mvn -q -DskipTests package
docker build -t rewabank/auth-service:latest .
docker push rewabank/auth-service:latest
docker rmi rewabank/auth-service:latest
docker builder prune -f
Set-Location $root

# Customers MS
Set-Location "$root\customers-service"
mvn -q -DskipTests package
docker build -t rewabank/customers-service:latest .
docker push rewabank/customers-service:latest
docker rmi rewabank/customers-service:latest
docker builder prune -f
Set-Location $root

# Accounts MS
Set-Location "$root\accounts-service"
mvn -q -DskipTests package
docker build -t rewabank/accounts-service:latest .
docker push rewabank/accounts-service:latest
docker rmi rewabank/accounts-service:latest
docker builder prune -f
Set-Location $root

# Transactions MS
Set-Location "$root\transactions-service"
mvn -q -DskipTests package
docker build -t rewabank/transactions-service:latest .
docker push rewabank/transactions-service:latest
docker rmi rewabank/transactions-service:latest
docker builder prune -f
Set-Location $root

# Fraud MS
Set-Location "$root\fraud-service"
mvn -q -DskipTests package
docker build -t rewabank/fraud-service:latest .
docker push rewabank/fraud-service:latest
docker rmi rewabank/fraud-service:latest
docker builder prune -f
Set-Location $root

# Notifications MS
Set-Location "$root\notifications-service"
mvn -q -DskipTests package
docker build -t rewabank/notifications-service:latest .
docker push rewabank/notifications-service:latest
docker rmi rewabank/notifications-service:latest
docker builder prune -f
Set-Location $root

# Audit MS
Set-Location "$root\audit-service"
mvn -q -DskipTests package
docker build -t rewabank/audit-service:latest .
docker push rewabank/audit-service:latest
docker rmi rewabank/audit-service:latest
docker builder prune -f
Set-Location $root

echo "All images built and pushed"
```

---

# STEP 9 — Deploy All Microservices

> All K8s manifests are in `k8s/apps/` (app deployments) and `k8s/istio/` (Istio policies).
> Each file is a combined manifest containing ServiceAccount + ConfigMap + Deployment + Service + HPA + PDB.

```powershell
$root = "C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025"
Set-Location $root

# 1. Apply Istio policies for all services
kubectl apply -f k8s\istio\api-gateway.yaml
kubectl apply -f k8s\istio\auth-service.yaml
kubectl apply -f k8s\istio\customers-service.yaml
kubectl apply -f k8s\istio\accounts-service.yaml
kubectl apply -f k8s\istio\transactions-service.yaml
kubectl apply -f k8s\istio\fraud-service.yaml
kubectl apply -f k8s\istio\notifications-service.yaml
kubectl apply -f k8s\istio\audit-service.yaml

# 2. Deploy services one at a time (dependency order matters)
# auth-ms first — customers-ms depends on Keycloak events it produces
kubectl apply -f k8s\apps\auth-service.yaml
kubectl wait --for=condition=ready pod -l app=auth-ms -n banking --timeout=180s

kubectl apply -f k8s\apps\customers-service.yaml
kubectl wait --for=condition=ready pod -l app=customers-ms -n banking --timeout=180s

kubectl apply -f k8s\apps\accounts-service.yaml
kubectl wait --for=condition=ready pod -l app=accounts-ms -n banking --timeout=180s

kubectl apply -f k8s\apps\fraud-service.yaml
kubectl wait --for=condition=ready pod -l app=fraud-ms -n banking --timeout=180s

kubectl apply -f k8s\apps\transactions-service.yaml
kubectl wait --for=condition=ready pod -l app=transactions-ms -n banking --timeout=180s

kubectl apply -f k8s\apps\notifications-service.yaml
kubectl wait --for=condition=ready pod -l app=notifications-ms -n banking --timeout=180s

kubectl apply -f k8s\apps\audit-service.yaml
kubectl wait --for=condition=ready pod -l app=audit-ms -n banking --timeout=180s

# api-gateway last — it routes to all other services
kubectl apply -f k8s\apps\api-gateway.yaml
kubectl wait --for=condition=ready pod -l app=api-gateway -n banking --timeout=180s

echo "All MS deployed"
kubectl get pods -n banking
# All pods should show 2/2 (app container + Istio envoy sidecar)
```

## Rebuild and redeploy a single service

> ⚠️ Always stop the cluster before building to avoid WSL2 OOM crash.

```powershell
$root = "C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025"

# Step 1 — Stop cluster to free RAM
k3d cluster stop rewabank

# Step 2 — Build the image (example: accounts-service)
Set-Location "$root\accounts-service"
mvn clean package -DskipTests
docker build -t rewabank/accounts-service:latest .
docker push rewabank/accounts-service:latest
docker rmi rewabank/accounts-service:latest
docker builder prune -f
Set-Location $root

# Step 3 — Restart cluster
.\k8s\start-cluster.ps1

# Step 4 — Rollout restart
kubectl rollout restart deployment accounts-ms -n banking
kubectl rollout status deployment accounts-ms -n banking
kubectl get pods -n banking
```

---

# STEP 10 — Istio Ingress Gateway (Permanent Access)

> This replaces all port-forward commands. Apply once — works forever.

```powershell
# Apply the Istio Gateway + VirtualService
kubectl apply -f "C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025\k8s\istio\api-gateway.yaml"

# Verify
kubectl get gateway -A
kubectl get virtualservice -A
```

## Fix nginx routing (required after every cluster start)

k3d's nginx loadbalancer routes port 80 → node port 80, but Istio ingressgateway
listens on NodePort 30759. This one-liner fixes the routing:

```powershell
docker exec k3d-rewabank-serverlb sh -c "sed -i 's/server k3d-rewabank-agent-0:80/server k3d-rewabank-agent-0:30759/g; s/server k3d-rewabank-server-0:80/server k3d-rewabank-server-0:30759/g' /etc/nginx/nginx.conf && nginx -s reload"
```

## ⭐ Startup Script — always use this to start the cluster

Save as `k8s/start-cluster.ps1` and run it every time instead of `k3d cluster start`:

```powershell
# k8s/start-cluster.ps1
k3d cluster start rewabank
Write-Host "Waiting for cluster..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# Fix nginx port routing — routes :80 to Istio NodePort 30759
docker exec k3d-rewabank-serverlb sh -c "sed -i 's/server k3d-rewabank-agent-0:80/server k3d-rewabank-agent-0:30759/g; s/server k3d-rewabank-server-0:80/server k3d-rewabank-server-0:30759/g' /etc/nginx/nginx.conf && nginx -s reload"

Write-Host "Cluster ready!" -ForegroundColor Green
Write-Host "API Gateway:    http://localhost/api/v1" -ForegroundColor Cyan
Write-Host "Keycloak Admin: http://localhost/auth/admin" -ForegroundColor Cyan
Write-Host "Keycloak Token: http://localhost/auth/realms/rewabank/protocol/openid-connect/token" -ForegroundColor Cyan
```

Run with:
```powershell
cd "C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025"
.\k8s\start-cluster.ps1
```

---

# STEP 11 — Configure Keycloak Realm

> No port-forward needed — Keycloak is accessible via Istio ingress at http://localhost/auth

```powershell
# Get admin token
$TOKEN = (Invoke-RestMethod `
  -Uri "http://localhost/auth/realms/master/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=admin-cli&username=admin&password=admin@2024"
).access_token

# Create rewabank realm
Invoke-RestMethod `
  -Uri "http://localhost/auth/admin/realms" `
  -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"} `
  -ContentType "application/json" `
  -Body '{"realm":"rewabank","enabled":true,"displayName":"RewaBank"}'

# ⚠️ CRITICAL: Set frontend URL so the iss claim in JWT tokens matches KEYCLOAK_ISSUER_URI on microservices.
#
# Choose ONE based on your setup:
#
# Option A — LOCAL ONLY (no Cloudflare tunnel):
#   frontendUrl = internal DNS. MS ConfigMaps must use the same internal DNS issuer URI.
Invoke-RestMethod `
  -Uri "http://localhost/auth/admin/realms/rewabank" `
  -Method PUT `
  -Headers @{Authorization="Bearer $TOKEN"} `
  -ContentType "application/json" `
  -Body '{"realm":"rewabank","enabled":true,"attributes":{"frontendUrl":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth"}}'
Write-Host "Frontend URL set to internal DNS (local)!" -ForegroundColor Green

# Option B — PRODUCTION (Cloudflare tunnel — api.rewarealm.com):
#   frontendUrl = public domain. MS ConfigMaps KEYCLOAK_ISSUER_URI must also use this domain.
#   setup-keycloak.ps1 sets this automatically — use it instead of the manual steps above:
#   & ".\k8s\setup-keycloak.ps1"

# Create rewabank-ms client
Invoke-RestMethod `
  -Uri "http://localhost/auth/admin/realms/rewabank/clients" `
  -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"} `
  -ContentType "application/json" `
  -Body '{"clientId":"rewabank-ms","secret":"rewabank-ms-secret-2024","redirectUris":["*"],"publicClient":false,"serviceAccountsEnabled":true,"directAccessGrantsEnabled":true,"enabled":true}'

# Create roles
$roles = @("CUSTOMER","TELLER","RELATIONSHIP_MANAGER","CREDIT_OFFICER","BRANCH_MANAGER","AUDITOR","SUPER_ADMIN")
foreach ($role in $roles) {
  Invoke-RestMethod `
    -Uri "http://localhost/auth/admin/realms/rewabank/roles" `
    -Method POST `
    -Headers @{Authorization="Bearer $TOKEN"} `
    -ContentType "application/json" `
    -Body "{`"name`":`"$role`"}"
}

# Grant manage-users permission to rewabank-ms service account
$CLIENT   = (Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/clients?clientId=rewabank-ms" -Headers @{Authorization="Bearer $TOKEN"}).id
$SA_USER  = (Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/clients/$CLIENT/service-account-user" -Headers @{Authorization="Bearer $TOKEN"}).id
$MGMT_CLIENT = (Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/clients?clientId=realm-management" -Headers @{Authorization="Bearer $TOKEN"}).id
$ADMIN_ROLE  = (Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/clients/$MGMT_CLIENT/roles/manage-users" -Headers @{Authorization="Bearer $TOKEN"})
Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/users/$SA_USER/role-mappings/clients/$MGMT_CLIENT" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body "[$($ADMIN_ROLE | ConvertTo-Json)]"

echo "Keycloak realm configured"
```

> ✅ Realm data (users, clients, roles) is persisted in `keycloak_db` (PostgreSQL) — survives pod restarts.
> Re-run `setup-keycloak.ps1` only if the realm is missing (e.g. after full cluster recreation).
> The `frontendUrl` setting is also persisted — no need to set it again after pod restarts.

---

# STEP 11.5 — Fix MS ConfigMap Issuer URIs

> ⚠️ **This step is required.** Some MS ConfigMaps ship with `:80` in the Keycloak issuer URI
> (e.g. `http://keycloak-keycloakx-http.keycloak.svc.cluster.local:80/auth/...`).
> However Keycloak drops the default port 80 from the `iss` claim it embeds in JWT tokens,
> issuing tokens with `http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/...` (no `:80`).
> Spring Security does an exact string match — this mismatch causes **401 Unauthorized on every endpoint**.
> Fix: ensure all configmaps use the URI without `:80`.

```powershell
# Patch JSON files — remove :80 from Keycloak URIs across all services
$patchAuth = '{"data":{"KEYCLOAK_ISSUER_URI":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/realms/rewabank","KEYCLOAK_JWKS_URI":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/realms/rewabank/protocol/openid-connect/certs","KEYCLOAK_URL":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth"}}'
$patchOthers = '{"data":{"KEYCLOAK_ISSUER_URI":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/realms/rewabank","KEYCLOAK_JWKS_URI":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/realms/rewabank/protocol/openid-connect/certs"}}'

$patchAuth   | Out-File "$PWD\patch-auth.json"   -Encoding utf8 -NoNewline
$patchOthers | Out-File "$PWD\patch-others.json" -Encoding utf8 -NoNewline

# Apply to all 8 deployed services
kubectl patch configmap auth-ms-config          -n banking --type merge --patch-file "$PWD\patch-auth.json"
kubectl patch configmap customers-ms-config     -n banking --type merge --patch-file "$PWD\patch-others.json"
kubectl patch configmap accounts-ms-config      -n banking --type merge --patch-file "$PWD\patch-others.json"
kubectl patch configmap transactions-ms-config  -n banking --type merge --patch-file "$PWD\patch-others.json"
kubectl patch configmap fraud-ms-config         -n banking --type merge --patch-file "$PWD\patch-others.json"
kubectl patch configmap notifications-ms-config -n banking --type merge --patch-file "$PWD\patch-others.json"
kubectl patch configmap audit-ms-config         -n banking --type merge --patch-file "$PWD\patch-others.json"
kubectl patch configmap api-gateway-config      -n banking --type merge --patch-file "$PWD\patch-others.json"

Write-Host "✅ All ConfigMaps updated!" -ForegroundColor Green

# Restart all services to pick up new config
kubectl rollout restart deployment/auth-ms          -n banking
kubectl rollout restart deployment/customers-ms     -n banking
kubectl rollout restart deployment/accounts-ms      -n banking
kubectl rollout restart deployment/transactions-ms  -n banking
kubectl rollout restart deployment/fraud-ms         -n banking
kubectl rollout restart deployment/notifications-ms -n banking
kubectl rollout restart deployment/audit-ms         -n banking
kubectl rollout restart deployment/api-gateway      -n banking

# Wait for all rollouts to complete
kubectl rollout status deployment/auth-ms          -n banking --timeout=120s
kubectl rollout status deployment/customers-ms     -n banking --timeout=120s
kubectl rollout status deployment/accounts-ms      -n banking --timeout=120s
kubectl rollout status deployment/transactions-ms  -n banking --timeout=120s
kubectl rollout status deployment/fraud-ms         -n banking --timeout=120s
kubectl rollout status deployment/notifications-ms -n banking --timeout=120s
kubectl rollout status deployment/audit-ms         -n banking --timeout=120s
kubectl rollout status deployment/api-gateway      -n banking --timeout=120s

kubectl get pods -n banking
Write-Host "✅ All services restarted with correct issuer config!" -ForegroundColor Green
```

> **Why JWKS URI keeps the internal DNS (no localhost)?**
> The `KEYCLOAK_JWKS_URI` is used server-side by each MS to fetch Keycloak's public signing keys.
> This happens inside the cluster — internal DNS is correct and more reliable than going via localhost.
> Only the `KEYCLOAK_ISSUER_URI` needs to match the `iss` claim in the JWT token.

---

# STEP 12 — Verify Everything

```powershell
# Check all pods — should be 2/2 (app + Envoy sidecar)
kubectl get pods -n banking
kubectl get pods -n keycloak

# Test API Gateway health — no port-forward needed
curl http://localhost/api/v1/actuator/health

# Test Keycloak realm
curl http://localhost/auth/realms/rewabank

# Test registration
$body = '{"fullName":"Test User","email":"test@rewabank.com","mobileNumber":"+91-9876543210","password":"Test@1234"}'
$body | Out-File -FilePath "mock\register.json" -Encoding utf8
curl.exe -i -X POST http://localhost/api/v1/auth/register -H "Content-Type: application/json" -H "Origin: http://localhost:4200" --data-binary "@mock\register.json"

# Test login
$loginBody = "grant_type=password&client_id=rewabank-ms&client_secret=rewabank-ms-secret-2024&username=test@rewabank.com&password=Test@1234"
Invoke-RestMethod -Uri "http://localhost/auth/realms/rewabank/protocol/openid-connect/token" -Method POST -ContentType "application/x-www-form-urlencoded" -Body $loginBody
```

---

# STEP 13 — Observability Dashboards

```powershell
# Kiali — live service mesh topology
istioctl dashboard kiali
# Opens at http://localhost:20001

# Grafana — metrics
istioctl dashboard grafana
# Opens at http://localhost:3000

# Jaeger — distributed tracing
istioctl dashboard jaeger
# Opens at http://localhost:16686

# Prometheus — raw metrics
istioctl dashboard prometheus
# Opens at http://localhost:9090

# Keycloak admin — no port-forward needed
# http://localhost/auth/admin
# admin / admin@2024
```

---

# STEP 14 — Angular Setup

## 14.1 Install Angular

```powershell
# Install Node.js first: https://nodejs.org — download LTS

npm install -g @angular/cli

ng new rewabank-web --routing --style=scss
cd rewabank-web

npm install keycloak-js
npm install keycloak-angular
```

## 14.2 Keycloak adapter setup

```typescript
// src/app/app.module.ts
import { NgModule, APP_INITIALIZER } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { KeycloakAngularModule, KeycloakService } from 'keycloak-angular';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';

function initializeKeycloak(keycloak: KeycloakService) {
  return () =>
    keycloak.init({
      config: {
        url: 'http://localhost/auth',   // ← Istio ingress — no port-forward
        realm: 'rewabank',
        clientId: 'rewabank-web',
      },
      initOptions: {
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri:
          window.location.origin + '/assets/silent-check-sso.html',
      },
    });
}

@NgModule({
  declarations: [AppComponent],
  imports: [BrowserModule, AppRoutingModule, HttpClientModule, KeycloakAngularModule],
  providers: [{
    provide: APP_INITIALIZER,
    useFactory: initializeKeycloak,
    multi: true,
    deps: [KeycloakService],
  }],
  bootstrap: [AppComponent],
})
export class AppModule {}
```

## 14.3 API service

```typescript
// src/app/services/api.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { KeycloakService } from 'keycloak-angular';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly BASE_URL = 'http://localhost/api/v1';  // ← Istio ingress

  constructor(private http: HttpClient, private keycloak: KeycloakService) {}

  private async getHeaders(): Promise<HttpHeaders> {
    const token = await this.keycloak.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    });
  }

  register(data: any): Observable<any> {
    return this.http.post(`${this.BASE_URL}/auth/register`, data);
  }

  async getMyAccounts(): Promise<Observable<any>> {
    const headers = await this.getHeaders();
    return this.http.get(`${this.BASE_URL}/accounts/my-accounts`, { headers });
  }

  async transfer(data: any, idempotencyKey: string): Promise<Observable<any>> {
    const headers = (await this.getHeaders()).set('X-Idempotency-Key', idempotencyKey);
    return this.http.post(`${this.BASE_URL}/transactions/transfer`, data, { headers });
  }

  async getBalance(accountNumber: string): Promise<Observable<any>> {
    const headers = await this.getHeaders();
    return this.http.get(`${this.BASE_URL}/accounts/${accountNumber}/balance`, { headers });
  }
}
```

## 14.4 Create rewabank-web Keycloak client

```powershell
# No port-forward needed
$TOKEN = (Invoke-RestMethod `
  -Uri "http://localhost/auth/realms/master/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=admin-cli&username=admin&password=admin@2024"
).access_token

Invoke-RestMethod `
  -Uri "http://localhost/auth/admin/realms/rewabank/clients" `
  -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"} `
  -ContentType "application/json" `
  -Body '{"clientId":"rewabank-web","publicClient":true,"redirectUris":["https://www.rewarealm.com/*","https://angular-banking-app.vercel.app/*","http://localhost:4200/*"],"webOrigins":["https://www.rewarealm.com","https://angular-banking-app.vercel.app","http://localhost:4200"],"attributes":{"post.logout.redirect.uris":"https://www.rewarealm.com/*##https://angular-banking-app.vercel.app/*"},"directAccessGrantsEnabled":true,"standardFlowEnabled":true,"enabled":true}'

# Or use setup-keycloak.ps1 — it creates/updates rewabank-web with production + Vercel + localhost in one shot:
# & ".\k8s\setup-keycloak.ps1"

echo "Angular Keycloak client created"
```

## 14.5 Run Angular

```powershell
ng serve
# App runs at: http://localhost:4200
# API Gateway: http://localhost/api/v1
# Keycloak:    http://localhost/auth
```

---

# Quick Reference — Local URLs

```
Service              URL                                              Notes
────────────────────────────────────────────────────────────────────────────────
API Gateway          http://localhost/api/v1                         Istio ingress (local)
Keycloak Admin       http://localhost/auth/admin                     Istio ingress (local)
Keycloak Token       http://localhost/auth/realms/rewabank/          Istio ingress (local)
                     protocol/openid-connect/token
Angular App          http://localhost:4200                           local dev
Kiali                http://localhost:20001                          istioctl dashboard
Grafana              http://localhost:3000                           istioctl dashboard
Jaeger               http://localhost:16686                          istioctl dashboard
Prometheus           http://localhost:9090                           istioctl dashboard

Production URLs (Cloudflare tunnel — api.rewarealm.com):
────────────────────────────────────────────────────────────────────────────────
API Gateway          https://api.rewarealm.com/api/v1
Keycloak Admin       https://api.rewarealm.com/auth/admin
Keycloak Token       https://api.rewarealm.com/auth/realms/rewabank/protocol/openid-connect/token
Angular App          https://www.rewarealm.com                       production
Angular App (test)   https://angular-banking-app.vercel.app          Vercel preview

⚠️ No port-forward needed for API Gateway or Keycloak.
   Use: .\k8s\start-cluster.ps1 to start the cluster.
   Run: cloudflared tunnel run rewabank (separate terminal) for external access.

Cluster Management:
Stop cluster:        k3d cluster stop rewabank
Start cluster:       .\k8s\start-cluster.ps1   ← always use this, not k3d directly
Delete cluster:      k3d cluster delete rewabank
List clusters:       k3d cluster list

Credentials:
Keycloak admin:      admin / <stored in keycloak-admin-secret — never in files>
Realm:               rewabank
Test user:           testuser / Test@1234  (SUPER_ADMIN role)
Angular client:      rewabank-web (public)
MS client:           rewabank-ms / rewabank-ms-secret-2024
Neon PostgreSQL:     see Neon dashboard → Roles for passwords — stored in per-service K8s secrets
Valkey (Redis):      redis@2024
MongoDB Atlas:       see Atlas dashboard — credentials stored in mongodb-atlas-secret

Database Credentials (Neon):
⚠️  Passwords are NOT stored in this file — get them from Neon dashboard → Roles
auth_db:             auth_user         → auth-db-secret (banking namespace)
customers_db:        customers_user    → customers-db-secret (banking namespace)
accounts_db:         accounts_user     → accounts-db-secret (banking namespace)
transactions_db:     transactions_user → transactions-db-secret (banking namespace)
fraud_db:            fraud_user        → fraud-db-secret (banking namespace)
audit_db:            neondb_owner      → audit-db-secret (banking namespace)
keycloak_db:         neondb_owner      → keycloak-db-secret (keycloak namespace)
payments_db:         payments_user     → payments-db-secret (banking namespace)
Connection:          jdbc:postgresql://<neon-host>:5432/<db>?sslmode=require

Internal Service Hostnames:
PostgreSQL (Neon):   <neon-host>.ap-south-1.aws.neon.tech:5432  ← cloud, not local
Valkey (Redis):      valkey.banking.svc.cluster.local:6379
Kafka:               kafka-kafka-bootstrap.banking.svc.cluster.local:9092
MongoDB (Atlas):     mongodb+srv://<cluster>.mongodb.net  ← cloud, not local (audit_db, notifications_db)
Keycloak:            keycloak-keycloakx-http.keycloak.svc.cluster.local  ← no :80 (port 80 is default, omitted in JWT iss claim)

Docker Image Names:
api-gateway          rewabank/api-gateway:latest
auth-service         rewabank/auth-service:latest
customers-service    rewabank/customers-service:latest
accounts-service     rewabank/accounts-service:latest
transactions-service rewabank/transactions-service:latest
fraud-service        rewabank/fraud-service:latest
notifications-service rewabank/notifications-service:latest
audit-service        rewabank/audit-service:latest
payments-service     rewabank/payments-service:latest

Deployment Names (for kubectl):
api-gateway, auth-ms, customers-ms, accounts-ms,
transactions-ms, fraud-ms, notifications-ms, audit-ms, payments-ms
```

---

# Troubleshooting

```powershell
# Pod not starting — check logs
kubectl logs -f deployment/auth-ms -n banking -c auth-ms

# Pod showing 1/2 instead of 2/2
kubectl get namespace banking --show-labels
# Should show: istio-injection=enabled

# CrashLoopBackOff
kubectl logs deployment/auth-ms -n banking -c auth-ms --previous

# Check ConfigMap
kubectl get configmap auth-ms-config -n banking -o yaml

# Check secret
kubectl get secret auth-db-secret -n banking

# mTLS connection refused
kubectl get authorizationpolicy -n banking

# Restart a deployment
kubectl rollout restart deployment/auth-ms -n banking

# http://localhost not working after cluster start
# Run the nginx fix:
docker exec k3d-rewabank-serverlb sh -c "sed -i 's/server k3d-rewabank-agent-0:80/server k3d-rewabank-agent-0:30759/g; s/server k3d-rewabank-server-0:80/server k3d-rewabank-server-0:30759/g' /etc/nginx/nginx.conf && nginx -s reload"
# Or just run: .\k8s\start-cluster.ps1

# Keycloak realm lost after pod restart
# Realm/users/clients are persisted in keycloak_db — they survive restarts
# Re-run setup-keycloak.ps1 if realm is missing (start-cluster.ps1 does this automatically):
$env:KEYCLOAK_ADMIN_PASSWORD = kubectl get secret keycloak-admin-secret -n keycloak -o jsonpath='{.data.password}' | ForEach-Object { [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_)) }; & ".\k8s\setup-keycloak.ps1"

# Keycloak pod stuck at 1/2 forever (Istio probe issue)
# Root cause: Istio strips /auth prefix from probe paths on management port 9000
# Fix: ensure keycloak-values.yaml has this under podAnnotations:
#   sidecar.istio.io/rewriteAppHTTPProbers: "false"
# Then: helm upgrade keycloak codecentric/keycloakx -n keycloak --values k8s\keycloak-values.yaml
#       kubectl delete pod keycloak-keycloakx-0 -n keycloak

# Keycloak Liquibase error: "idx_login_failure_expire already exists"
# Root cause: Keycloak 26.x migration bug — index created twice across changelog versions
# Fix: Neon SQL Editor → select keycloak_db → run:
#   DROP INDEX IF EXISTS public.idx_login_failure_expire;
# Then: kubectl delete pod keycloak-keycloakx-0 -n keycloak

# Services crash with "password authentication failed" after Neon schema wipe
# Root cause: schema wipe + role recreation resets passwords — K8s secrets have old passwords
# Fix: Neon dashboard → Roles → reset each role password → update K8s secrets:
foreach ($svc in @("auth","customers","accounts","transactions","fraud","payments")) {
  kubectl create secret generic "$svc-db-secret" -n banking `
    --from-literal=username="${svc}_user" `
    --from-literal=password="NEW_PASSWORD" `
    --dry-run=client -o yaml | kubectl apply -f -
}
kubectl rollout restart deployment/auth-ms deployment/accounts-ms deployment/customers-ms deployment/transactions-ms deployment/fraud-ms deployment/payments-ms -n banking

# All endpoints returning 401 even with valid token
# Root cause: Keycloak issues tokens with iss=https://api.rewarealm.com/auth but MS expect that URI
# Fix: frontendUrl is set by setup-keycloak.ps1 automatically — re-run it
$env:KEYCLOAK_ADMIN_PASSWORD = kubectl get secret keycloak-admin-secret -n keycloak -o jsonpath='{.data.password}' | ForEach-Object { [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_)) }; & ".\k8s\setup-keycloak.ps1"

# User registered but gets 401 on protected endpoints (no CUSTOMER role)
# Root cause: user was created before Keycloak was fully configured — CUSTOMER role not assigned
# Fix: assign CUSTOMER role manually
$ADMIN_TOKEN = (Invoke-RestMethod -Uri "http://localhost/auth/realms/master/protocol/openid-connect/token" -Method POST -ContentType "application/x-www-form-urlencoded" -Body "grant_type=password&client_id=admin-cli&username=admin&password=admin@2024").access_token
$role = Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/roles/CUSTOMER" -Headers @{Authorization="Bearer $ADMIN_TOKEN"}
Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/users/<KEYCLOAK_USER_ID>/role-mappings/realm" -Method POST -Headers @{Authorization="Bearer $ADMIN_TOKEN"} -ContentType "application/json" -Body "[$($role | ConvertTo-Json)]"
# Get fresh token after assigning role

# Empty error body on API call (no error message at all)
# Root cause: JWT token expired (TTL is 300 seconds)
# Fix: always get a fresh token before testing
$TOKEN = (Invoke-RestMethod -Uri "http://localhost/auth/realms/rewabank/protocol/openid-connect/token" -Method POST -ContentType "application/x-www-form-urlencoded" -Body "grant_type=password&client_id=rewabank-web&username=EMAIL&password=PASSWORD").access_token

# CORS blocked from www.rewarealm.com (Access-Control-Allow-Origin missing)
# Root cause: CORS_ORIGIN_WEB in api-gateway ConfigMap was set to the old Vercel URL,
# and CORS_ORIGIN_MOBILE was mistakenly set to api.rewarealm.com (the API itself, not a frontend)
# Fix: update k8s/apps/api-gateway.yaml ConfigMap and reapply
kubectl apply -f k8s/apps/api-gateway.yaml
kubectl rollout restart deployment/api-gateway -n banking
kubectl rollout status deployment/api-gateway -n banking
# Correct values: CORS_ORIGIN_WEB=https://www.rewarealm.com, CORS_ORIGIN_MOBILE=https://angular-banking-app.vercel.app

# Keycloak redirect_uri mismatch (invalid_redirect_uri error on login)
# Root cause: www.rewarealm.com not in rewabank-web client's Valid Redirect URIs
# Fix: re-run setup-keycloak.ps1 (it upserts the client with all three origins)
& ".\k8s\setup-keycloak.ps1"

# Gateway rewritePath causing 404 on microservice endpoints
# Root cause: rewritePath strips /api/v1/service/ prefix but MS controller expects full path
# Fix: remove rewritePath filter from the affected route in ApiGatewayApplication.java
# Then rebuild: mvn -q -DskipTests package && docker build/push && kubectl rollout restart deployment/api-gateway -n banking

# OTP verify returning AUTH_500 (unexpected error)
# Root cause: malformed JSON body — $OTP_CODE variable contained a psql error message
# Fix: always insert test OTP using pgcrypto crypt() via Neon SQL Editor (auth_db):
#   CREATE EXTENSION IF NOT EXISTS pgcrypto;
#   UPDATE otp_records SET used=true WHERE user_id='<USER_ID>';
#   INSERT INTO otp_records (user_id, otp_hash, purpose, expires_at)
#     VALUES ('<USER_ID>', crypt('123456', gen_salt('bf',10)), 'TRANSFER', NOW() + INTERVAL '5 minutes');

# Service fails to start — "Connection refused" or "SSL required" against Neon
# Root cause 1: DB_HOST env var not set or wrong — check secret was created correctly
kubectl get secret accounts-db-secret -n banking -o jsonpath='{.data.host}' | [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_))
# Root cause 2: Missing ?sslmode=require in JDBC URL — Neon requires SSL
# Fix: verify application.yml datasource URL ends with ?sslmode=require
# Root cause 3: Neon compute suspended — first connection after idle takes 1-3s
# Fix: hit the Neon dashboard to wake it up, or just retry the service

# payments-ms CreateContainerConfigError on first deploy
# Root cause: razorpay-secret not created — payments-ms references it but it wasn't in Step 7
# Fix: create the secret (use placeholder values if no real Razorpay credentials yet)
kubectl create secret generic razorpay-secret -n banking `
  --from-literal=key-id=<YOUR_RAZORPAY_KEY_ID> `
  --from-literal=key-secret=<YOUR_RAZORPAY_KEY_SECRET> `
  --from-literal=webhook-secret=<YOUR_RAZORPAY_WEBHOOK_SECRET>

# Service gets "too many connections" error from Neon
# Root cause: HikariCP pool size × number of services > 20 (Neon free tier limit)
# Fix: set maximum-pool-size: 2 in each service's application.yml datasource.hikari config
# Current allocation: 7 services × 2 + Keycloak 4 = 18 connections (within limit)

# Keycloak fails to start after cluster restart
# Root cause: Neon compute is suspended when Keycloak tries to connect
# Fix: wait 10-15 seconds after cluster start before checking Keycloak pod status
# Keycloak will retry DB connection automatically — give it 2-3 minutes

# Full reset — delete everything and start fresh
k3d cluster delete rewabank
k3d cluster create rewabank `
  --agents 1 `
  --port "80:80@loadbalancer" `
  --port "443:443@loadbalancer" `
  --k3s-arg "--disable=traefik@server:0" `
  --k3s-arg "--disable=servicelb@server:0"
kubectl create namespace banking
kubectl create namespace keycloak
kubectl label namespace banking istio-injection=enabled
kubectl label namespace keycloak istio-injection=enabled
# Then re-run from STEP 3
```

---

# API Endpoint Verification

> Run these after Step 11 to verify all services are working end to end.
> Prerequisites: cluster running, all pods 2/2, setup-keycloak.ps1 completed.
> ⚠️ Token expires in 300 seconds — always get a fresh token before each test block.

## Get token (run this first — rerun whenever token expires)

```powershell
$TOKEN = (Invoke-RestMethod `
  -Uri "http://localhost/auth/realms/rewabank/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=rewabank-web&username=mu@gmail.com&password=Test@1234"
).access_token
Write-Host "Token: $($TOKEN.Substring(0,40))..." -ForegroundColor Green
```

## Auth MS endpoints ✅ Verified

```powershell
# Register new user
# ⚠️ mobileNumber must be +91-XXXXXXXXXX format (regex: ^\+91-?[0-9]{10}$)
Invoke-RestMethod -Uri "http://localhost/api/v1/auth/register" -Method POST `
  -ContentType "application/json" `
  -Body '{"email":"newuser@rewabank.com","mobileNumber":"+91-9876543210","password":"Test@1234","fullName":"New User"}'
# Expected: 201 Created — keycloakUserId, email, maskedMobile returned
# Note: CUSTOMER role is auto-assigned in Keycloak after creation

# Get current user profile
Invoke-RestMethod -Uri "http://localhost/api/v1/auth/me" -Headers @{Authorization="Bearer $TOKEN"}
# Expected: userId, email, fullName, maskedMobile, kycVerified, status

# Generate OTP (banking operation — NOT for login)
Invoke-RestMethod -Uri "http://localhost/api/v1/auth/otp/generate" -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" `
  -Body '{"purpose":"TRANSFER"}'
# Expected: success=true, maskedMobile, expiresInSeconds=300
# Valid purposes: TRANSFER | CARD_BLOCK | CARD_UNBLOCK | BENEFICIARY_ADD | PASSWORD_RESET | DEVICE_REGISTER

# Verify OTP
# ⚠️ OTP is BCrypt hashed — cannot read from DB directly
# Insert test OTP via Neon SQL Editor (neon.tech dashboard → auth_db → SQL Editor):
#   CREATE EXTENSION IF NOT EXISTS pgcrypto;
#   UPDATE otp_records SET used=true WHERE user_id='<BANKING_USER_ID>';
#   INSERT INTO otp_records (user_id, otp_hash, purpose, expires_at)
#     VALUES ('<BANKING_USER_ID>', crypt('123456', gen_salt('bf',10)), 'TRANSFER', NOW() + INTERVAL '5 minutes');
# Then verify:
Invoke-RestMethod -Uri "http://localhost/api/v1/auth/otp/verify" -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" `
  -Body '{"otp":"123456","purpose":"TRANSFER"}'
# Expected: success=true, message="OTP verified successfully"
```

## Customers MS endpoints ✅ Verified

```powershell
# Get my customer profile (auto-created via Kafka event after registration)
$customer = Invoke-RestMethod -Uri "http://localhost/api/v1/customers/me" -Headers @{Authorization="Bearer $TOKEN"}
$CUSTOMER_ID = $customer.id
# Expected: id, keycloakUserId, email, fullName, maskedMobile, kycStatus=NOT_SUBMITTED

# Submit KYC
# ⚠️ address must be a nested object — NOT a plain string
# ⚠️ aadhaarNumber regex: ^[2-9]{1}[0-9]{11}$ (starts with 2-9, 12 digits total)
# ⚠️ panNumber regex: ^[A-Z]{5}[0-9]{4}[A-Z]{1}$ (e.g. ABCDE1234F)
Invoke-RestMethod -Uri "http://localhost/api/v1/kyc/submit" -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" `
  -Body '{
    "aadhaarNumber": "234567890123",
    "panNumber": "ABCDE1234F",
    "dateOfBirth": "1990-01-01",
    "address": {
      "addressLine1": "123 Test Street",
      "addressLine2": "Near Park",
      "city": "Mumbai",
      "state": "Maharashtra",
      "pincode": "400001"
    }
  }'
# Expected: message="KYC submitted successfully. Our team will review within 2 business days."

# Get KYC status (used internally by Accounts MS)
Invoke-RestMethod -Uri "http://localhost/api/v1/customers/$CUSTOMER_ID/kyc-status" -Headers @{Authorization="Bearer $TOKEN"}
# Expected: customerId, kycStatus=SUBMITTED, kycVerified=false
```

## Accounts MS endpoints ✅ Verified

```powershell
# Get customer ID first (if not already set)
$CUSTOMER_ID = (Invoke-RestMethod -Uri "http://localhost/api/v1/customers/me" -Headers @{Authorization="Bearer $TOKEN"}).id

# Create account (requires X-Customer-Id header — must come from customers/me)
$acct = Invoke-RestMethod -Uri "http://localhost/api/v1/accounts" -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"; "X-Customer-Id"=$CUSTOMER_ID} `
  -ContentType "application/json" `
  -Body '{"accountType":"SAVINGS","branchCode":"BR001","ifscCode":"REWA0001234"}'
$ACCOUNT_ID     = $acct.id
$ACCOUNT_NUMBER = $acct.accountNumber
# Expected: 201 Created — status=PENDING (needs TELLER to activate)
# Valid accountTypes: SAVINGS | CURRENT

# Get my accounts
Invoke-RestMethod -Uri "http://localhost/api/v1/accounts/my-accounts" -Headers @{Authorization="Bearer $TOKEN"}

# Get account by ID
Invoke-RestMethod -Uri "http://localhost/api/v1/accounts/$ACCOUNT_ID" -Headers @{Authorization="Bearer $TOKEN"}

# Get balance (CQRS Redis read)
Invoke-RestMethod -Uri "http://localhost/api/v1/accounts/$ACCOUNT_NUMBER/balance" -Headers @{Authorization="Bearer $TOKEN"}
# Expected: accountNumber, balance, availableBalance, currency, status
# ⚠️ availableBalance may show -1000.00 on new PENDING accounts — Redis CQRS quirk

# Staff-only endpoints (require TELLER / BRANCH_MANAGER / SUPER_ADMIN role)
# PATCH /api/v1/accounts/{id}/activate  — activate account
# PATCH /api/v1/accounts/{id}/freeze    — freeze account
# PATCH /api/v1/accounts/{id}/unfreeze  — unfreeze account
# PATCH /api/v1/accounts/{id}/close     — close account permanently
```

## Transactions MS endpoints

```powershell
# Get transaction history (paginated)
Invoke-RestMethod -Uri "http://localhost/api/v1/transactions" -Headers @{Authorization="Bearer $TOKEN"}

# Transfer (requires X-Idempotency-Key header — use a new UUID each time)
# ⚠️ Never retry a transfer without a new idempotency key
Invoke-RestMethod -Uri "http://localhost/api/v1/transactions/transfer" -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"; "X-Idempotency-Key"=[System.Guid]::NewGuid().ToString()} `
  -ContentType "application/json" `
  -Body "{`"fromAccountNumber`":`"$ACCOUNT_NUMBER`",`"toAccountNumber`":`"DEST_ACCOUNT_NUMBER`",`"amount`":100,`"currency`":`"INR`",`"remarks`":`"Test transfer`"}"
# ⚠️ Both accounts must exist and be ACTIVE (not PENDING)
# ⚠️ Requires account to be activated by TELLER first

# Get transaction by ID
Invoke-RestMethod -Uri "http://localhost/api/v1/transactions/<TRANSACTION_ID>" -Headers @{Authorization="Bearer $TOKEN"}
```

## Known issues & fixes applied

```
Issue                              Fix applied
─────────────────────────────────────────────────────────────────────────────
All endpoints 401                  Keycloak frontendUrl set to internal DNS (Step 11)
ConfigMap :80 issuer mismatch      Removed :80 from all MS ConfigMaps (Step 11.5)
Customers MS Access Denied         Added issuer-uri to customers-ms application.yml
KYC route 503                      Added /api/v1/kyc/** route to ApiGatewayApplication.java
KYC submit 500 (address mismatch)  Fixed Address entity: pincode → @Column(name="postal_code")
Accounts 404 on GET endpoints      Removed rewritePath filter from accounts route in gateway
mobileNumber validation failing    Format must be +91-XXXXXXXXXX not plain 10 digits
CORS blocked (www.rewarealm.com)   CORS_ORIGIN_WEB=https://www.rewarealm.com,
                                   CORS_ORIGIN_MOBILE=https://angular-banking-app.vercel.app
                                   (k8s/apps/api-gateway.yaml ConfigMap)
Keycloak redirect_uri rejected     Added https://www.rewarealm.com/* to rewabank-web
                                   redirectUris + webOrigins + post.logout.redirect.uris
                                   (k8s/setup-keycloak.ps1 — re-run to apply)
```

---

# RAM Saving Tips

```powershell
# Stop cluster completely when not using
k3d cluster stop rewabank

# Start again — always use the startup script
.\k8s\start-cluster.ps1

# Scale down replicas while developing (already 1 — scale to 0 for unused services)
kubectl scale deployment notifications-ms --replicas=0 -n banking
kubectl scale deployment audit-ms         --replicas=0 -n banking
kubectl scale deployment fraud-ms         --replicas=0 -n banking

# Scale back up when needed
kubectl scale deployment notifications-ms --replicas=1 -n banking
kubectl scale deployment audit-ms         --replicas=1 -n banking
kubectl scale deployment fraud-ms         --replicas=1 -n banking

# Scale down Istio addons when not needed (saves ~500MB RAM)
kubectl scale deployment kiali   --replicas=0 -n istio-system
kubectl scale deployment grafana --replicas=0 -n istio-system
kubectl scale deployment jaeger  --replicas=0 -n istio-system

# Scale back up for observability
kubectl scale deployment kiali grafana jaeger --replicas=1 -n istio-system

# Check current RAM usage
kubectl top nodes
kubectl top pods -n banking
kubectl top pods -n istio-system
```

---

# STEP 15 — Performance / Load Testing

> All load-testing scripts live in the `load-test/` folder.
> `run-load-test.ps1` is the entry point — it checks Docker, starts the cluster if needed,
> waits for pods, gets a JWT token, then runs the load generator and prints a
> WORKING / BREAKING / RISK report per endpoint.

```powershell
cd "C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025"

# Full automation (defaults: user newuser@rewabank.com, 50 users, 60s)
.\load-test\run-load-test.ps1

# With all parameters
.\load-test\run-load-test.ps1 -User "testuser" -Password "Test@1234" -Users 50 -DurationSeconds 120
```

## Skip services that aren't deployed

> `loans-service` and `cards-service` are intentionally not deployed on the 16GB machine.
> `load-test.ps1` skips them by default (`-DisabledServices "loans-service","cards-service"`)
> so they don't show up as false BREAKING 503 failures and skew the overall verdict.

```powershell
# Drive the generator directly with an existing token
$TOKEN = .\load-test\get-token.ps1 -User "testuser" -Password "Test@1234"

# Default skip (loans + cards)
.\load-test\load-test.ps1 -Token $TOKEN -Users 50 -DurationSeconds 120

# Also skip fraud (e.g. when scaled to 0 for RAM)
.\load-test\load-test.ps1 -Token $TOKEN -DisabledServices "loans-service","cards-service","fraud-service"

# Test everything — skip nothing
.\load-test\load-test.ps1 -Token $TOKEN -DisabledServices @()
```
//arghoCd username: admin password: argho@Admin2026
> **Note:** the generator has a built-in 1-second think-time per virtual user,
> so throughput is capped at roughly `Users` req/s. A 2-user run tops out at ~2 req/s —
> that's by design, not a bottleneck. Use 50+ users for a meaningful throughput number.

---

# STEP 16 — Argo CD GitOps (automated deploy)

Argo CD runs **inside** the cluster (namespace `argocd`) and continuously syncs the
manifests under `k8s/apps` + `k8s/istio` from the **`master`** branch on GitHub into the
`banking` namespace. Push a manifest change → Argo deploys it. No manual `kubectl apply`.

> **The split:** Argo handles **config** (timeouts, replicas, env, Istio VS/DR). It does
> **not** build images. Images come from the **CI pipeline** (`.github/workflows/ci.yml`):
> on a push to `master`, CI builds + scans + pushes to Docker Hub, then pins the sha256
> **digest** into `k8s/apps/<svc>.yaml` and commits it back to `master`. Argo sees the
> manifest change and deploys it; k3d pulls the image from Docker Hub (images are public).

## One-time install
```powershell
# Docker Desktop + the k3d 'rewabank' cluster must be up first
.\k8s\argocd\install-argocd.ps1 -Branch master
```
This installs Argo CD, configures `argocd-server` for the `/argocd` ingress route
(`--insecure` + `server.rootpath=/argocd`), and registers two Applications:
`rewabank-apps` (watches `k8s/apps`) and `rewabank-istio` (watches `k8s/istio`).

## Access the UI — permanent URL (no port-forward)
A `/argocd` route is wired into the Istio ingress (`k8s/istio/api-gateway.yaml`), so:

| Access | URL |
|---|---|
| Local | `http://localhost/argocd` |
| External (needs `cloudflared tunnel run rewabank`) | `https://api.rewarealm.com/argocd` |

**Login:** `admin` / `sdiYnqVpYKm61XBJ`
> (On a fresh install the password is the auto-generated one in the
> `argocd-initial-admin-secret` — change it to the above via the UI / `argocd account update-password`.)

Fallback if the route is ever down:
```powershell
kubectl port-forward svc/argocd-server -n argocd 8090:443   # then https://localhost:8090
```

## Staggered rollout (sync-waves) — avoids the reconnect storm
Deploying all DB-backed services at once triggers a thundering-herd reconnect against
external **Neon Postgres** + **MongoDB** → `500` health → liveness restart loop. Each
Deployment carries an `argocd.argoproj.io/sync-wave` annotation so Argo rolls **≤2 at a
time**, never two services on the same external DB together, waiting for each wave to be
Healthy before the next:

| Wave | Services |
|---|---|
| 1 | api-gateway + auth-ms |
| 2 | fraud-ms + customers-ms |
| 3 | audit-ms + accounts-ms |
| 4 | notifications-ms + transactions-ms |
| 5 | payments-ms |

## Disabled / parked services (RAM)
- **loans-ms + cards-ms** — moved to `k8s/apps-disabled/` (out of Argo's `k8s/apps` path).
  They target an in-cluster Postgres that isn't deployed and have no image here. Re-enable
  by moving the file back into `k8s/apps/`.
- **notifications-ms** — `replicas: 0` in Git, HPA removed (it was the top CPU user). It's a
  pure async Kafka consumer, so Kafka retains events and it catches up when scaled back up.

## Common operations
```powershell
# See Application status
kubectl get applications -n argocd

# Disable auto-sync (take manual control — e.g. to sync one resource at a time in the UI)
kubectl patch application rewabank-apps -n argocd --type merge -p '{\"spec\":{\"syncPolicy\":{\"automated\":null}}}'

# Re-enable auto-sync (selfHeal on, prune off) — rolls out in sync-waves
kubectl patch application rewabank-apps -n argocd --type merge -p '{\"spec\":{\"syncPolicy\":{\"automated\":{\"selfHeal\":true,\"prune\":false}}}}'

# Force Argo to re-read GitHub immediately
kubectl annotate application rewabank-apps -n argocd argocd.argoproj.io/refresh=hard --overwrite

# One-off local image build (bypass CI) — build, import into k3d, roll the deployment
.\k8s\argocd\build-and-import.ps1 -Service api-gateway
```

> **RAM note:** the Docker Desktop VM (~10.4 GB) sits ~80% full with the full stack + Argo's
> 7 pods even when idle. A mass rollout surge can tip it over → restart churn. Keep
> notifications parked and loans/cards disabled, or raise Docker RAM to 14–16 GB. See the
> RAM Saving Tips section above.
