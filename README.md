
# RewaBank — Microservices Platform

Local deployment guide for k3d on Windows with Docker Desktop + Istio service mesh.
Tested on: Windows 11, 16GB RAM, Docker Desktop with WSL2.

---

## Prerequisites (install once)

```powershell
winget install k3d
winget install Kubernetes.kubectl
winget install Helm.Helm
winget install Istio.istioctl
```

**Docker Desktop settings:**
- Resources → Memory: `10GB`, CPUs: `4`, Swap: `2GB`
- Kubernetes → **Disable** built-in Kubernetes (we use k3d instead)

**WSL2 memory cap** (run as Administrator, then restart Docker Desktop):
```powershell
@"
[wsl2]
memory=10GB
processors=4
swap=2GB
"@ | Set-Content C:\Users\$env:USERNAME\.wslconfig
wsl --shutdown
```

---

## STEP 1 — Create k3d Cluster (once)

```powershell
k3d cluster create rewabank `
  --agents 1 `
  --port "80:80@loadbalancer" `
  --port "443:443@loadbalancer" `
  --k3s-arg "--disable=traefik@server:0" `
  --k3s-arg "--disable=servicelb@server:0"

kubectl get nodes
# Expected: k3d-rewabank-server-0 (control-plane) + k3d-rewabank-agent-0 (Ready)
```

### Fix kubectl kubeconfig after every cluster start

`host.docker.internal` breaks on Windows after restart. Always run this after starting the cluster:

```powershell
# Find the port mapped to 6443 (changes after every cluster restart)
docker ps --filter name=k3d-rewabank-serverlb --format "{{.Ports}}"
# Example output: 0.0.0.0:59408->6443/tcp  ← use this port

kubectl config set-cluster k3d-rewabank --server=https://127.0.0.1:59408
kubectl get nodes
```

---

## STEP 2 — Install Istio

```powershell
istioctl install --set profile=demo -y

kubectl get pods -n istio-system
# Expected: istiod, istio-ingressgateway, istio-egressgateway all Running
```

---

## STEP 3 — Namespaces + Sidecar Injection + mTLS

```powershell
kubectl create namespace banking
kubectl create namespace keycloak

# Both MUST have istio-injection=enabled
# keycloak needs it so Keycloak can reach PostgreSQL through mTLS STRICT
kubectl label namespace banking istio-injection=enabled
kubectl label namespace keycloak istio-injection=enabled

kubectl apply -f k8s\mtls-strict.yaml

kubectl get namespace banking keycloak --show-labels
# Both should show: istio-injection=enabled
```

---

## STEP 4 — Add Helm Repos

```powershell
# Postgres (Neon) and MongoDB (Atlas) are cloud-managed — no operator Helm repos needed.
helm repo add strimzi     https://strimzi.io/charts/
helm repo add codecentric https://codecentric.github.io/helm-charts
helm repo update
```

---

## STEP 5 — Deploy Infrastructure

### 5.1 PostgreSQL (Neon — cloud managed)

PostgreSQL is **cloud-managed on [Neon](https://neon.tech)**, not deployed in-cluster. Each
service gets a `DB_URL` (+ `DB_USERNAME`/`DB_PASSWORD`) injected via its ConfigMap/Secret
(see `k8s/apps/*.yaml`, e.g. `jdbc:postgresql://<endpoint>.neon.tech:5432/accounts_db?sslmode=require`).

1. Create a Neon project and one database per service (`auth_db`, `customers_db`, `accounts_db`,
   `transactions_db`, `fraud_db`, `payments_db`, `audit_db`, `keycloak_db`).
2. Put the pooled connection endpoint in each service's `DB_URL` (ConfigMap) and the password in
   the `*-db-secret` Secret. Schema is created by Flyway on startup (`ddl-auto: validate`).

> The old in-cluster CloudNativePG setup is preserved at `k8s/legacy/postgres-cluster.yaml`
> for offline/local-only use. It is no longer part of the standard deploy.

### 5.2 Valkey (Redis)

```powershell
kubectl apply -f k8s\valkey.yaml
kubectl wait --for=condition=ready pod -l app=valkey -n banking --timeout=180s
```

### 5.3 Kafka (Strimzi)

> **Note:** Strimzi 1.0.0 uses `kafka.strimzi.io/v1` (not `v1beta2`). The `k8s\kafka-cluster.yaml` is already updated.

```powershell
helm install strimzi strimzi/strimzi-kafka-operator --namespace banking --set watchNamespaces="{banking}"
kubectl wait --for=condition=ready pod -l name=strimzi-cluster-operator -n banking --timeout=120s

# Wait for CRDs to register before applying cluster
kubectl get crd | Select-String kafka
# Must show kafkas.kafka.strimzi.io before proceeding

kubectl apply -f k8s\kafka-cluster.yaml
kubectl wait kafka/kafka --for=condition=Ready --timeout=300s -n banking
```

### 5.4 MongoDB (Atlas — cloud managed)

MongoDB is **cloud-managed on [MongoDB Atlas](https://www.mongodb.com/atlas)**, used by
`notifications-ms` and `audit-ms`. Each gets a `MONGODB_URI` injected from the
`mongodb-atlas-secret` Secret (DB `notifications_db` / `audit_db`).

```powershell
# One Atlas cluster, SRV URI per service DB, stored in a K8s Secret:
kubectl create secret generic mongodb-atlas-secret --namespace banking `
  --from-literal=notifications-uri='mongodb+srv://<user>:<pass>@<cluster>.mongodb.net/notifications_db?retryWrites=true&w=majority' `
  --from-literal=audit-uri='mongodb+srv://<user>:<pass>@<cluster>.mongodb.net/audit_db?retryWrites=true&w=majority'
```

> Add your cluster's egress IP to the Atlas **Network Access** allow-list (residential IPs
> rotate — a dropped match causes TLS `internal_error` crash-loops). Seed notification
> templates with `k8s/seed-notification-templates.js` (see that file's header).
> The old in-cluster operator setup is preserved at `k8s/legacy/mongodb-cluster.yaml`.

### 5.5 Keycloak

> **Why keycloak namespace needs istio-injection:** The banking namespace uses mTLS STRICT.
> Without an Istio sidecar, PostgreSQL rejects the Keycloak connection with a TLS handshake reset.

```powershell
# Keycloak DB secret (in keycloak namespace)
kubectl create secret generic keycloak-db-secret --namespace keycloak --from-literal=username=keycloak_user --from-literal=password=keycloak@2024

# Copy PostgreSQL CA cert to keycloak namespace
# Use .data.ca\.crt jsonpath (single quotes inside jsonpath break on Windows PowerShell)
$rawB64 = kubectl get secret postgresql-ca -n banking -o jsonpath="{.data.ca\.crt}"
$decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($rawB64))
$decoded = $decoded -replace "`r`n", "`n"
[System.IO.File]::WriteAllText("$PWD\ca.crt", $decoded, [System.Text.UTF8Encoding]::new($false))
kubectl create secret generic postgresql-ca --namespace keycloak --from-file=ca.crt=.\ca.crt

# Verify cert (must start with -----BEGIN CERTIFICATE-----)
Get-Content .\ca.crt | Select-Object -First 2

helm install keycloak codecentric/keycloakx --namespace keycloak --values k8s\keycloak-values.yaml
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=keycloakx -n keycloak --timeout=300s

# Verify: pod must have 2 containers (keycloak + istio-proxy)
kubectl get pod keycloak-keycloakx-0 -n keycloak -o jsonpath="{.spec.containers[*].name}"
# Expected: keycloak istio-proxy
```

---

## STEP 6 — Create K8s Secrets

```powershell
kubectl create secret generic auth-db-secret --namespace banking --from-literal=username=auth_user --from-literal=password=auth@2024
kubectl create secret generic customers-db-secret --namespace banking --from-literal=username=customers_user --from-literal=password=customers@2024
kubectl create secret generic accounts-db-secret --namespace banking --from-literal=username=accounts_user --from-literal=password=accounts@2024
kubectl create secret generic transactions-db-secret --namespace banking --from-literal=username=transactions_user --from-literal=password=transactions@2024
kubectl create secret generic fraud-db-secret --namespace banking --from-literal=username=fraud_user --from-literal=password=fraud@2024
kubectl create secret generic audit-db-secret --namespace banking --from-literal=username=audit_user --from-literal=password=audit@2024
kubectl create secret generic redis-secret --namespace banking --from-literal=password=redis@2024
kubectl create secret generic keycloak-ms-secret --namespace banking --from-literal=client-id=rewabank-ms --from-literal=client-secret=rewabank-ms-secret-2024
kubectl create secret generic encryption-secret --namespace banking --from-literal=aes-key=$([Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 })))
kubectl create secret generic minio-secret --namespace banking --from-literal=access-key=rewabank-minio --from-literal=secret-key=minio@2024
kubectl get secrets -n banking
```

---

## STEP 7 — Build Docker Images

> **RAM constraint:** 16GB laptop — only build and deploy 7 core services.
> loans-service, cards-service, audit-service are skipped (deploy later if needed).

> **Fix applied:** Each service `.dockerignore` had `target/` which blocked the JAR from the build context.
> Added `!target/*.jar` after `target/` in all service `.dockerignore` files — already fixed in this repo.

```powershell
$root = "C:\Users\Monukushw\Downloads\Microservices-22-04-2025"

# Build BOM first (required once)
Set-Location $root\rewabank-bom; mvn install -q; Set-Location $root

# Build all JARs
mvn clean package -DskipTests

# Login to Docker Hub
docker login

# Build + push + cleanup one service at a time (saves disk space)
$services = @("api-gateway","auth-service","customers-service","accounts-service","transactions-service","fraud-service","notifications-service")

foreach ($svc in $services) {
  Write-Host "=== Building $svc ===" -ForegroundColor Cyan
  Set-Location "$root\$svc"
  docker build -t "rewabank/$($svc):latest" .
  docker push "rewabank/$($svc):latest"
  docker rmi "rewabank/$($svc):latest"
  docker builder prune -f
  Set-Location $root
  Write-Host "=== $svc done ===" -ForegroundColor Green
}
```

> **Note:** Use `$($svc)` not `$svc` inside strings with `:latest` suffix —
> PowerShell treats `$svc:latest` as a scoped variable and resolves it to empty.

---

## STEP 8 — Deploy Microservices

```powershell
kubectl apply -f k8s\apps\api-gateway.yaml
kubectl apply -f k8s\apps\auth-service.yaml
kubectl apply -f k8s\apps\customers-service.yaml
kubectl apply -f k8s\apps\accounts-service.yaml
kubectl apply -f k8s\apps\transactions-service.yaml
kubectl apply -f k8s\apps\fraud-service.yaml
kubectl apply -f k8s\apps\notifications-service.yaml

kubectl apply -f k8s\istio\api-gateway.yaml
kubectl apply -f k8s\istio\auth-service.yaml
kubectl apply -f k8s\istio\customers-service.yaml
kubectl apply -f k8s\istio\accounts-service.yaml
kubectl apply -f k8s\istio\transactions-service.yaml
kubectl apply -f k8s\istio\fraud-service.yaml
kubectl apply -f k8s\istio\notifications-service.yaml

kubectl rollout status deployment -n banking --timeout=300s
kubectl get pods -n banking
# All pods should show 2/2 (app + Envoy sidecar)
```

---

## STEP 9 — Istio Ingress + Nginx Fix

> The k3d loadbalancer routes :80 → node port 80, but Istio ingressgateway uses a different NodePort.
> Find the actual NodePort and fix nginx routing after every cluster start.

```powershell
# Find the actual NodePort assigned to Istio ingress port 80
kubectl get svc istio-ingressgateway -n istio-system -o jsonpath='{.spec.ports[?(@.port==80)].nodePort}'
# Example output: 31947  ← use this value below

docker exec k3d-rewabank-serverlb sh -c "sed -i 's/agent-0:80/agent-0:31947/g' /etc/nginx/nginx.conf"
docker exec k3d-rewabank-serverlb sh -c "sed -i 's/server-0:80/server-0:31947/g' /etc/nginx/nginx.conf"
docker exec k3d-rewabank-serverlb sh -c "nginx -s reload"
```

> **Note:** Use short sed patterns (`agent-0:PORT`) not full hostnames — long commands wrap in PowerShell.
> The NodePort (31947 in our case) is fixed per cluster creation but resets if you delete and recreate the cluster.

Test:
```powershell
curl.exe -s http://localhost/api/v1/actuator/health
# Returns 401 Unauthorized = gateway is working (Istio blocking unauthenticated requests as expected)
```

---

## STEP 10 — Configure Keycloak Realm

Run the setup script (handles token refresh automatically):

```powershell
.\k8s\setup-keycloak.ps1
```

> **Why the script refreshes the token mid-way:** Setting `frontendUrl` on the realm invalidates
> the current admin token. The script gets a fresh token immediately after that step.

> **Important:** Always use short variable names in PowerShell for Keycloak API calls.
> Long `Invoke-RestMethod` lines wrap at ~170 chars and break mid-command causing parser errors.

---

## STEP 11 — Fix ConfigMap Issuer URIs

> Keycloak drops port 80 from JWT `iss` claims (standard HTTP behavior).
> All MS ConfigMaps shipped with `:80` in the issuer URI — Spring Security does exact match → 401.
> Fix: remove `:80` from all ConfigMaps. Already fixed in `k8s/apps/*.yaml` in this repo.

Apply the fixed ConfigMaps and restart:

```powershell
kubectl apply -f k8s\apps\api-gateway.yaml
kubectl apply -f k8s\apps\auth-service.yaml
kubectl apply -f k8s\apps\customers-service.yaml
kubectl apply -f k8s\apps\accounts-service.yaml
kubectl apply -f k8s\apps\transactions-service.yaml
kubectl apply -f k8s\apps\fraud-service.yaml
kubectl apply -f k8s\apps\notifications-service.yaml
kubectl rollout restart deployment -n banking
kubectl rollout status deployment -n banking --timeout=300s
```

---

## STEP 12 — Verify

```powershell
kubectl get pods -n banking
# All 7 services should be 2/2 Running

# Register a user (save body to file to avoid PowerShell line-wrap issues)
'{"email":"test@rewabank.com","mobileNumber":"+91-9876543210","password":"Test@1234","fullName":"Test User"}' | Out-File -FilePath "$PWD\register.json" -Encoding utf8 -NoNewline
curl.exe -s -X POST http://localhost/api/v1/auth/register -H "Content-Type: application/json" -d "@register.json"
# Expected: 201 with keycloakUserId, email, maskedMobile

# Get token
$uri = "http://localhost/auth/realms/rewabank/protocol/openid-connect/token"
$b = "grant_type=password&client_id=rewabank-ms&client_secret=rewabank-ms-secret-2024&username=test@rewabank.com&password=Test@1234"
$TOKEN = (Invoke-RestMethod -Uri $uri -Method POST -ContentType "application/x-www-form-urlencoded" -Body $b).access_token
Write-Host "Token: $($TOKEN.Substring(0,40))..."
```

---

## Startup Script (subsequent starts)

Always use `start-cluster.ps1` instead of `k3d cluster start` directly:

```powershell
.\k8s\start-cluster.ps1
```

Then fix the kubeconfig:
```powershell
docker ps --filter name=k3d-rewabank-serverlb --format "{{.Ports}}"
kubectl config set-cluster k3d-rewabank --server=https://127.0.0.1:<PORT>
kubectl get nodes
```

---

## Rebuild & Redeploy a Single Service

```powershell
$root = "C:\Users\Monukushw\Downloads\Microservices-22-04-2025"
$svc  = "accounts-service"

Set-Location "$root\$svc"
mvn clean package -DskipTests -q
docker build -t "rewabank/$($svc):latest" .
docker push "rewabank/$($svc):latest"
docker rmi "rewabank/$($svc):latest"
kubectl rollout restart deployment/accounts-ms -n banking
kubectl rollout status deployment/accounts-ms -n banking
Set-Location $root
```

---

## RAM Saving Tips (16 GB laptop)

```powershell
# Scale down observability when not needed
kubectl scale deployment kiali grafana jaeger --replicas=0 -n istio-system

# Scale down services not currently testing
kubectl scale deployment notifications-ms --replicas=0 -n banking

# Stop cluster completely when done for the day
k3d cluster stop rewabank

# Scale everything back up
kubectl scale deployment --all --replicas=1 -n banking
```

---

## Known Issues & Fixes Applied

| Issue | Fix |
|---|---|
| `kubectl` can't connect after cluster start | `host.docker.internal` breaks on Windows — use `127.0.0.1` with the k3d port |
| Kafka CRDs not found on apply | Wait for CRDs to register after Strimzi operator starts |
| Strimzi `v1beta2` not found | Strimzi 1.0.0 uses `v1` — updated `kafka-cluster.yaml` |
| Docker build fails — no JAR | `.dockerignore` excluded `target/` — added `!target/*.jar` to all services |
| `$svc:latest` resolves empty | PowerShell scoped variable — use `$($svc):latest` |
| MongoDB pod not ready | `mongodb-secret` must be created before applying cluster yaml |
| MongoDB SCRAM-SHA-1 not enabled | Added `authMechanism=SCRAM-SHA-256` to notifications-ms MongoDB URI |
| auth-ms Flyway index clash | V1 and V4 both created same index names — renamed V4 partial indexes |
| All endpoints 401 | Keycloak `frontendUrl` must be set to internal DNS (no `:80`) |
| Keycloak admin 401 after frontendUrl change | `frontendUrl` change invalidates token — refresh immediately after |
| nginx routing broken | `sed` with long patterns wraps in PowerShell — use short patterns |
| Nginx wrong NodePort | NodePort is dynamic (31947 in our case) — always check with `kubectl get svc` |
| Role assignment 403 on registration | `rewabank-ms` service account needed `manage-realm` + `manage-clients` in addition to `manage-users` |
| GW_ERR_001 on `/auth/register` | Missing `/fallback/auth` endpoint in FallbackController — added fix |
| GW_ERR_001 after rollout restart | auth-ms was still starting when request arrived — wait for `2/2 Running` before testing |
| 409 on re-registration | Previous partial registration left user in DB — use different email or delete orphan |

---

## Quick Reference

```
URL                                                  Notes
──────────────────────────────────────────────────────────────────
http://localhost/api/v1                              API Gateway (Istio ingress)
http://localhost/auth/admin                          Keycloak Admin UI
http://localhost/auth/realms/rewabank/               Keycloak token endpoint
  protocol/openid-connect/token

Credentials:
  Keycloak admin:    admin / admin@2024
  MS client:         rewabank-ms / rewabank-ms-secret-2024
  PostgreSQL:        rewabank@2024
  Valkey (Redis):    redis@2024
  MongoDB:           mongodb@2024 (user: root)

Endpoints (no :80 — Keycloak drops default port from JWT iss):
  PostgreSQL:  Neon (cloud)  — per-service DB_URL Secret (…neon.tech:5432, sslmode=require)
  MongoDB:     Atlas (cloud) — MONGODB_URI Secret (…mongodb.net/<db>)
  Valkey:      valkey.banking.svc.cluster.local:6379            (in-cluster)
  Kafka:       kafka-kafka-bootstrap.banking.svc.cluster.local:9092  (in-cluster)
  Keycloak:    keycloak-keycloakx-http.keycloak.svc.cluster.local  (no :80)
```

---

## Troubleshooting

**kubectl can't connect after cluster start**
```powershell
docker ps --filter name=k3d-rewabank-serverlb --format "{{.Ports}}"
kubectl config set-cluster k3d-rewabank --server=https://127.0.0.1:<PORT>
```

**Pod showing 1/2 instead of 2/2**
```powershell
kubectl get namespace banking --show-labels
# Must show istio-injection=enabled
```

**All endpoints 401 after registration**
```powershell
# Re-run Keycloak setup (token refresh is built in)
.\k8s\setup-keycloak.ps1
```

**http://localhost not working after cluster start**
```powershell
kubectl get svc istio-ingressgateway -n istio-system -o jsonpath='{.spec.ports[?(@.port==80)].nodePort}'
docker exec k3d-rewabank-serverlb sh -c "sed -i 's/agent-0:80/agent-0:<PORT>/g' /etc/nginx/nginx.conf"
docker exec k3d-rewabank-serverlb sh -c "sed -i 's/server-0:80/server-0:<PORT>/g' /etc/nginx/nginx.conf"
docker exec k3d-rewabank-serverlb sh -c "nginx -s reload"
```

**CrashLoopBackOff**
```powershell
kubectl logs deployment/<name> -n banking -c <name> --previous
kubectl describe pod -l app=<name> -n banking
```

**OTel log noise (blocks seeing real errors)**
```powershell
kubectl set env deployment/<name> -n banking MANAGEMENT_OTLP_TRACING_EXPORT_ENABLED=false
```

**Full reset**
```powershell
k3d cluster delete rewabank
k3d cluster create rewabank --agents 1 --port "80:80@loadbalancer" --port "443:443@loadbalancer" --k3s-arg "--disable=traefik@server:0" --k3s-arg "--disable=servicelb@server:0"
# Re-run from STEP 2
```