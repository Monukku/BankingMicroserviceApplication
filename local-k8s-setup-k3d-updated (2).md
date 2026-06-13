# RewaBank — Local K8s + Istio Setup
## k3d on Windows 16GB RAM (Lightweight — No Docker Desktop K8s)

---

## Overview

```
Step 1  — Docker Desktop (engine only) + k3d cluster
Step 2  — Install istioctl on Windows
Step 3  — Install Istio (demo profile)
Step 4  — Namespaces + sidecar injection
Step 5  — Helm repos
Step 6  — Deploy infra (PostgreSQL, Redis, Kafka, Keycloak, MongoDB)
Step 7  — K8s Secrets
Step 8  — Build Docker images locally
Step 9  — Deploy all MS
Step 10 — Istio Ingress Gateway (permanent access — no port-forward)
Step 11 — Configure Keycloak realm
Step 12 — Verify + test
Step 13 — Observability Dashboards
Step 14 — Angular setup
```

> **No port-forward needed anywhere in this guide.**
> All services are accessible via Istio Ingress at `http://localhost`.

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

# Install observability addons
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/kiali.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/prometheus.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/grafana.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/jaeger.yaml

kubectl wait --for=condition=ready pod -l app=kiali -n istio-system --timeout=120s
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
kubectl apply -f C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\mtls-strict.yaml

echo "Namespaces ready"
```

---

# STEP 5 — Add Helm Repos

```powershell
winget install Helm.Helm

helm repo remove bitnami 2>$null

helm repo add cnpg        https://cloudnative-pg.github.io/charts
helm repo add strimzi     https://strimzi.io/charts/
helm repo add mongodb     https://mongodb.github.io/helm-charts
helm repo add codecentric https://codecentric.github.io/helm-charts

helm repo update
helm repo list
```

---

# STEP 6 — Deploy Infrastructure

## 6.1 PostgreSQL (CloudNativePG)

```powershell
helm install cnpg cnpg/cloudnative-pg --namespace cnpg-system --create-namespace
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=cloudnative-pg -n cnpg-system --timeout=120s

kubectl create secret generic postgresql-secret --namespace banking --from-literal=username=postgres --from-literal=password=rewabank@2024

kubectl apply -f C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\postgres-cluster.yaml
kubectl wait --for=condition=ready pod -l cnpg.io/cluster=postgresql -n banking --timeout=300s

$PG_POD = kubectl get pod -n banking -l cnpg.io/cluster=postgresql -o jsonpath='{.items[0].metadata.name}'

kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE auth_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE customers_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE accounts_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE transactions_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE fraud_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE audit_db;"

kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE USER auth_user WITH PASSWORD 'auth@2024'; CREATE USER customers_user WITH PASSWORD 'customers@2024'; CREATE USER accounts_user WITH PASSWORD 'accounts@2024'; CREATE USER transactions_user WITH PASSWORD 'transactions@2024'; CREATE USER fraud_user WITH PASSWORD 'fraud@2024'; CREATE USER audit_user WITH PASSWORD 'audit@2024'; GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_user; GRANT ALL PRIVILEGES ON DATABASE customers_db TO customers_user; GRANT ALL PRIVILEGES ON DATABASE accounts_db TO accounts_user; GRANT ALL PRIVILEGES ON DATABASE transactions_db TO transactions_user; GRANT ALL PRIVILEGES ON DATABASE fraud_db TO fraud_user; GRANT ALL PRIVILEGES ON DATABASE audit_db TO audit_user;"

kubectl exec -it $PG_POD -n banking -- psql -U postgres -d auth_db -c "GRANT ALL ON SCHEMA public TO auth_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d customers_db -c "GRANT ALL ON SCHEMA public TO customers_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d accounts_db -c "GRANT ALL ON SCHEMA public TO accounts_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d transactions_db -c "GRANT ALL ON SCHEMA public TO transactions_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d fraud_db -c "GRANT ALL ON SCHEMA public TO fraud_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d audit_db -c "GRANT ALL ON SCHEMA public TO audit_user;"

# Keycloak DB + user
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE keycloak_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE USER keycloak_user WITH PASSWORD 'keycloak@2024';"

# Note: existing users created before Keycloak was fully configured
# may not have the CUSTOMER role — assign manually if needed:
# $ADMIN_TOKEN = (Invoke-RestMethod -Uri "http://localhost/auth/realms/master/protocol/openid-connect/token" -Method POST -ContentType "application/x-www-form-urlencoded" -Body "grant_type=password&client_id=admin-cli&username=admin&password=admin@2024").access_token
# $role = Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/roles/CUSTOMER" -Headers @{Authorization="Bearer $ADMIN_TOKEN"}
# Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/users/<USER_ID>/role-mappings/realm" -Method POST -Headers @{Authorization="Bearer $ADMIN_TOKEN"} -ContentType "application/json" -Body "[$($role | ConvertTo-Json)]"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE keycloak_db TO keycloak_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d keycloak_db -c "GRANT ALL ON SCHEMA public TO keycloak_user;"

echo "PostgreSQL ready"
```

## 6.2 Valkey (Redis)

```powershell
kubectl apply -f C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\valkey.yaml
kubectl wait --for=condition=ready pod -l app=valkey -n banking --timeout=180s
echo "Valkey ready"
# Use: valkey.banking.svc.cluster.local:6379  password: redis@2024
```

## 6.3 Kafka (Strimzi)

```powershell
helm install strimzi strimzi/strimzi-kafka-operator --namespace banking --set watchNamespaces="{banking}"
kubectl wait --for=condition=ready pod -l name=strimzi-cluster-operator -n banking --timeout=120s

kubectl apply -f C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\kafka-cluster.yaml
kubectl wait kafka/kafka --for=condition=Ready --timeout=300s -n banking

echo "Kafka ready"
# Use: kafka-kafka-bootstrap.banking.svc.cluster.local:9092
```

## 6.4 MongoDB

```powershell
helm install mongodb-operator mongodb/community-operator --namespace banking
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=mongodb-kubernetes-operator -n banking --timeout=120s

kubectl create secret generic mongodb-secret --namespace banking --from-literal=password=mongodb@2024
kubectl apply -f C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\mongodb-cluster.yaml
kubectl wait --for=condition=ready pod -l app=mongodb-svc -n banking --timeout=300s

echo "MongoDB ready"
# Use: mongodb-svc.banking.svc.cluster.local:27017
```

## 6.5 Keycloak

> ⚠️ **keycloak namespace MUST have `istio-injection=enabled`** (done in Step 4).
> The banking namespace uses mTLS STRICT — without an Istio sidecar on the Keycloak pod,
> PostgreSQL rejects the connection with a TLS handshake reset.
> The CA cert MUST be copied using `--from-file` not `--from-literal` to avoid double base64 encoding.

```powershell
# Step A: Create Keycloak DB secret
kubectl create secret generic keycloak-db-secret --namespace keycloak `
  --from-literal=username=keycloak_user `
  --from-literal=password=keycloak@2024

# Step B: Copy PostgreSQL CA cert from banking → keycloak namespace
# ⚠️ Use --from-file NOT --from-literal (from-literal double-encodes the cert)
$rawB64 = kubectl get secret postgresql-ca -n banking -o jsonpath="{.data['ca.crt']}"
$decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($rawB64))
$decoded = $decoded -replace "`r`n", "`n"
[System.IO.File]::WriteAllText("$PWD\ca.crt", $decoded, [System.Text.UTF8Encoding]::new($false))
kubectl create secret generic postgresql-ca --namespace keycloak --from-file=ca.crt=.\ca.crt

# Verify cert looks correct (must start with -----BEGIN CERTIFICATE-----)
Get-Content .\ca.crt | Select-Object -First 2

# Step C: Install Keycloak
helm install keycloak codecentric/keycloakx --namespace keycloak `
  --values C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\keycloak-values.yaml

kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=keycloakx -n keycloak --timeout=300s

# Verify pod has 2 containers: keycloak + istio-proxy (confirms mTLS sidecar is present)
kubectl get pod keycloak-keycloakx-0 -n keycloak -o jsonpath="{.spec.containers[*].name}"
# Expected output: keycloak istio-proxy

# Verify Keycloak connected to PostgreSQL (look for "Initializing database schema")
kubectl logs keycloak-keycloakx-0 -n keycloak --tail=10

echo "Keycloak ready"
# Internal DNS: keycloak-keycloakx-http.keycloak.svc.cluster.local:80
# External URL (after Step 10): http://localhost/auth/admin
```

---

# STEP 7 — K8s Secrets

```powershell
# PostgreSQL secrets
kubectl create secret generic auth-db-secret --namespace banking --from-literal=username=auth_user --from-literal=password=auth@2024
kubectl create secret generic customers-db-secret --namespace banking --from-literal=username=customers_user --from-literal=password=customers@2024
kubectl create secret generic accounts-db-secret --namespace banking --from-literal=username=accounts_user --from-literal=password=accounts@2024
kubectl create secret generic transactions-db-secret --namespace banking --from-literal=username=transactions_user --from-literal=password=transactions@2024
kubectl create secret generic fraud-db-secret --namespace banking --from-literal=username=fraud_user --from-literal=password=fraud@2024
kubectl create secret generic audit-db-secret --namespace banking --from-literal=username=audit_user --from-literal=password=audit@2024

# Valkey (Redis)
kubectl create secret generic redis-secret --namespace banking --from-literal=password=redis@2024

# Keycloak DB (in keycloak namespace — created in Step 6.5, listed here for reference)
# kubectl create secret generic keycloak-db-secret --namespace keycloak --from-literal=username=keycloak_user --from-literal=password=keycloak@2024

# Keycloak MS client (in banking namespace — used by microservices)
kubectl create secret generic keycloak-ms-secret --namespace banking --from-literal=client-id=rewabank-ms --from-literal=client-secret=rewabank-ms-secret-2024

# MongoDB
kubectl create secret generic mongodb-secret --namespace banking --from-literal=password=mongodb@2024

# AES-256 encryption key
$aesKey = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
kubectl create secret generic encryption-secret --namespace banking --from-literal=aes-key=$aesKey

# MinIO
kubectl create secret generic minio-secret --namespace banking --from-literal=access-key=rewabank-minio --from-literal=secret-key=minio@2024

echo "All secrets created"
kubectl get secrets -n banking
```

---

# STEP 8 — Build Docker Images

```powershell
$DOCKERHUB = "rewabank"
$root = "C:\Users\Monukushw\Downloads\Microservices-22-04-2025"

# API Gateway
cd $root\apigateway
mvn -q -DskipTests package
docker build -t $DOCKERHUB/rewabank-api-gateway:latest .
docker push $DOCKERHUB/rewabank-api-gateway:latest
docker rmi $DOCKERHUB/rewabank-api-gateway:latest
docker builder prune -f
cd $root

# Auth MS
cd $root\authms
mvn -q -DskipTests package
docker build -t $DOCKERHUB/rewabank-auth-ms:latest .
docker push $DOCKERHUB/rewabank-auth-ms:latest
docker rmi $DOCKERHUB/rewabank-auth-ms:latest
docker builder prune -f
cd $root

# Customers MS
cd $root\customerms
mvn -q -DskipTests package
docker build -t $DOCKERHUB/rewabank-customers-ms:latest .
docker push $DOCKERHUB/rewabank-customers-ms:latest
docker rmi $DOCKERHUB/rewabank-customers-ms:latest
docker builder prune -f
cd $root

# Accounts MS
cd $root\accounts
mvn -q -DskipTests package
docker build -t $DOCKERHUB/rewabank-accounts-ms:latest .
docker push $DOCKERHUB/rewabank-accounts-ms:latest
docker rmi $DOCKERHUB/rewabank-accounts-ms:latest
docker builder prune -f
cd $root

# Transactions MS
cd $root\transaction-service
mvn -q -DskipTests package
docker build -t $DOCKERHUB/rewabank-transactions-ms:latest .
docker push $DOCKERHUB/rewabank-transactions-ms:latest
docker rmi $DOCKERHUB/rewabank-transactions-ms:latest
docker builder prune -f
cd $root

# Fraud MS
cd $root\fraudms
mvn -q -DskipTests package
docker build -t $DOCKERHUB/rewabank-fraud-ms:latest .
docker push $DOCKERHUB/rewabank-fraud-ms:latest
docker rmi $DOCKERHUB/rewabank-fraud-ms:latest
docker builder prune -f
cd $root

# Notifications MS
cd $root\notificationms
mvn -q -DskipTests package
docker build -t $DOCKERHUB/rewabank-notifications-ms:latest .
docker push $DOCKERHUB/rewabank-notifications-ms:latest
docker rmi $DOCKERHUB/rewabank-notifications-ms:latest
docker builder prune -f
cd $root

# Audit MS
cd $root\audit-service
mvn -q -DskipTests package
docker build -t $DOCKERHUB/rewabank-audit-ms:latest .
docker push $DOCKERHUB/rewabank-audit-ms:latest
docker rmi $DOCKERHUB/rewabank-audit-ms:latest
docker builder prune -f
cd $root

echo "All images built and pushed"
```

---

# STEP 9 — Deploy All Microservices

```powershell
$root = "C:\Users\Monukushw\Downloads\Microservices-22-04-2025"
cd $root

# 1. ServiceAccounts
kubectl apply -f apigateway/K8s/serviceaccount.yaml
kubectl apply -f authms/k8s/serviceaccount.yaml
kubectl apply -f customerms/K8s/serviceaccount.yaml
kubectl apply -f accounts/k8s/serviceaccount.yaml
kubectl apply -f fraudms/k8s/serviceaccount.yaml
kubectl apply -f transaction-service/k8s/serviceaccount.yaml
kubectl apply -f notificationms/k8s/serviceaccount.yaml
kubectl apply -f audit-service/k8s/serviceaccount.yaml

# 2. ConfigMaps
kubectl apply -f apigateway/K8s/configmap.yaml
kubectl apply -f authms/k8s/configmap.yaml
kubectl apply -f customerms/K8s/configmap.yaml
kubectl apply -f accounts/k8s/configmap.yaml
kubectl apply -f fraudms/k8s/configmap.yaml
kubectl apply -f transaction-service/k8s/configmap.yaml
kubectl apply -f notificationms/k8s/configmap.yaml
kubectl apply -f audit-service/k8s/configmap.yaml

# 3. Istio policies
kubectl apply -f apigateway/K8s/peer-auth.yaml
kubectl apply -f apigateway/K8s/authz-policy.yaml
kubectl apply -f apigateway/K8s/destination-rule.yaml
kubectl apply -f apigateway/K8s/virtual-service.yaml

kubectl apply -f authms/k8s/peer-auth.yaml
kubectl apply -f authms/k8s/authz-policy.yaml
kubectl apply -f authms/k8s/destination-rule.yaml
kubectl apply -f authms/k8s/virtual-service.yaml

kubectl apply -f customerms/K8s/peer-auth.yaml
kubectl apply -f customerms/K8s/authz-policy.yaml
kubectl apply -f customerms/K8s/destination-rule.yaml
kubectl apply -f customerms/K8s/virtual-service.yaml

kubectl apply -f accounts/k8s/peer-auth.yaml
kubectl apply -f accounts/k8s/authz-policy.yaml
kubectl apply -f accounts/k8s/destination-rule.yaml
kubectl apply -f accounts/k8s/virtual-service.yaml

kubectl apply -f fraudms/k8s/peer-auth.yaml
kubectl apply -f fraudms/k8s/authz-policy.yaml
kubectl apply -f fraudms/k8s/destination-rule.yaml
kubectl apply -f fraudms/k8s/virtual-service.yaml

kubectl apply -f transaction-service/k8s/peer-auth.yaml
kubectl apply -f transaction-service/k8s/authz-policy.yaml
kubectl apply -f transaction-service/k8s/destination-rule.yaml
kubectl apply -f transaction-service/k8s/virtual-service.yaml

kubectl apply -f notificationms/k8s/peer-auth.yaml
kubectl apply -f notificationms/k8s/authz-policy.yaml

kubectl apply -f audit-service/k8s/peer-auth.yaml
kubectl apply -f audit-service/k8s/authz-policy.yaml

# 4. Deployments + Services (one at a time)
kubectl apply -f authms/k8s/deployment.yaml
kubectl apply -f authms/k8s/service.yaml
kubectl apply -f authms/k8s/hpa.yaml
kubectl wait --for=condition=ready pod -l app=auth-ms -n banking --timeout=180s

kubectl apply -f customerms/K8s/deployment.yaml
kubectl apply -f customerms/K8s/service.yaml
kubectl apply -f customerms/K8s/hpa.yaml
kubectl wait --for=condition=ready pod -l app=customers-ms -n banking --timeout=180s

kubectl apply -f accounts/k8s/deployment.yaml
kubectl apply -f accounts/k8s/service.yaml
kubectl apply -f accounts/k8s/hpa.yaml
kubectl wait --for=condition=ready pod -l app=accounts-ms -n banking --timeout=180s

kubectl apply -f fraudms/k8s/deployment.yaml
kubectl apply -f fraudms/k8s/service.yaml
kubectl apply -f fraudms/k8s/hpa.yaml
kubectl wait --for=condition=ready pod -l app=fraud-ms -n banking --timeout=180s

kubectl apply -f transaction-service/k8s/deployment.yaml
kubectl apply -f transaction-service/k8s/service.yaml
kubectl apply -f transaction-service/k8s/hpa.yaml
kubectl wait --for=condition=ready pod -l app=transactions-ms -n banking --timeout=180s

kubectl apply -f notificationms/k8s/deployment.yaml
kubectl apply -f notificationms/k8s/service.yaml
kubectl apply -f notificationms/k8s/hpa.yaml
kubectl wait --for=condition=ready pod -l app=notifications-ms -n banking --timeout=180s

kubectl apply -f audit-service/k8s/deployment.yaml
kubectl apply -f audit-service/k8s/service.yaml
kubectl apply -f audit-service/k8s/hpa.yaml
kubectl wait --for=condition=ready pod -l app=audit-ms -n banking --timeout=180s

kubectl apply -f apigateway/K8s/deployment.yaml
kubectl apply -f apigateway/K8s/service.yaml
kubectl apply -f apigateway/K8s/hpa.yaml
kubectl wait --for=condition=ready pod -l app=api-gateway -n banking --timeout=180s

echo "All MS deployed"
```

## Rebuild and redeploy a single service

```powershell
cd "C:\Users\Monukushw\Downloads\Microservices-22-04-2025\apigateway"
mvn clean package -DskipTests
docker build -t rewabank/rewabank-api-gateway:latest .
docker push rewabank/rewabank-api-gateway:latest
docker rmi rewabank/rewabank-api-gateway:latest
kubectl rollout restart deployment api-gateway -n banking
kubectl rollout status deployment api-gateway -n banking
kubectl get pods -n banking
```

---

# STEP 10 — Istio Ingress Gateway (Permanent Access)

> This replaces all port-forward commands. Apply once — works forever.

```powershell
# Apply the Istio Gateway + VirtualService
kubectl apply -f "C:\Users\Monukushw\Downloads\Microservices-22-04-2025\apigateway\K8s\ingress-gateway.yaml"

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
cd "C:\Users\Monukushw\Downloads\Microservices-22-04-2025"
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

# ⚠️ CRITICAL: Set frontend URL to internal DNS
# Without this, tokens have iss=http://localhost/auth/... but all MS ConfigMaps expect
# iss=http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/...
# This mismatch causes 401 Unauthorized on every protected endpoint.
Invoke-RestMethod `
  -Uri "http://localhost/auth/admin/realms/rewabank" `
  -Method PUT `
  -Headers @{Authorization="Bearer $TOKEN"} `
  -ContentType "application/json" `
  -Body '{"realm":"rewabank","enabled":true,"attributes":{"frontendUrl":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth"}}'
Write-Host "Frontend URL set to internal DNS!" -ForegroundColor Green

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
$CLIENT = (Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/clients?clientId=rewabank-ms" -Headers @{Authorization="Bearer $TOKEN"}).id
$SA_USER = (Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/clients/$CLIENT/service-account-user" -Headers @{Authorization="Bearer $TOKEN"}).id
$MGMT_CLIENT = (Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/clients?clientId=realm-management" -Headers @{Authorization="Bearer $TOKEN"}).id
$ADMIN_ROLE = (Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/clients/$MGMT_CLIENT/roles/manage-users" -Headers @{Authorization="Bearer $TOKEN"})
Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank/users/$SA_USER/role-mappings/clients/$MGMT_CLIENT" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body "[$($ADMIN_ROLE | ConvertTo-Json)]"

echo "Keycloak realm configured"
```

> ✅ Realm data (users, clients, roles) is persisted in `keycloak_db` (PostgreSQL) — survives pod restarts.
> Re-run `setup-keycloak.ps1` only if the realm is missing (e.g. after full cluster recreation).
> The `frontendUrl` setting is also persisted — no need to set it again after pod restarts.

---

# STEP 11.5 — Fix MS ConfigMap Issuer URIs

> ⚠️ **This step is required.** All MS ConfigMaps ship with `:80` in the Keycloak issuer URI
> (e.g. `http://keycloak-keycloakx-http.keycloak.svc.cluster.local:80/auth/...`).
> However Keycloak drops the default port 80 from the `iss` claim it embeds in JWT tokens,
> issuing tokens with `http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/...` (no `:80`).
> Spring Security does an exact string match — this mismatch causes **401 Unauthorized on every endpoint**.
> Fix: remove `:80` from all configmaps so the issuer strings match exactly.

```powershell
# Remove :80 from Keycloak issuer URI in all MS ConfigMaps
$patchAuth = '{"data":{"KEYCLOAK_ISSUER_URI":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/realms/rewabank","KEYCLOAK_JWKS_URI":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/realms/rewabank/protocol/openid-connect/certs","KEYCLOAK_URL":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth"}}'
$patchOthers = '{"data":{"KEYCLOAK_ISSUER_URI":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/realms/rewabank","KEYCLOAK_JWKS_URI":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/realms/rewabank/protocol/openid-connect/certs"}}'

$patchAuth   | Out-File "$PWD\patch-auth.json"   -Encoding utf8 -NoNewline
$patchOthers | Out-File "$PWD\patch-others.json" -Encoding utf8 -NoNewline

kubectl patch configmap auth-ms-config       -n banking --type merge --patch-file "$PWD\patch-auth.json"
kubectl patch configmap customers-ms-config  -n banking --type merge --patch-file "$PWD\patch-others.json"
kubectl patch configmap accounts-ms-config   -n banking --type merge --patch-file "$PWD\patch-others.json"
kubectl patch configmap api-gateway-config   -n banking --type merge --patch-file "$PWD\patch-others.json"

Write-Host "✅ All ConfigMaps updated!" -ForegroundColor Green

# Restart all services to pick up new config
kubectl rollout restart deployment/auth-ms       -n banking
kubectl rollout restart deployment/customers-ms  -n banking
kubectl rollout restart deployment/accounts-ms   -n banking
kubectl rollout restart deployment/api-gateway   -n banking

kubectl rollout status deployment/auth-ms      -n banking --timeout=120s
kubectl rollout status deployment/customers-ms -n banking --timeout=120s
kubectl rollout status deployment/accounts-ms  -n banking --timeout=120s
kubectl rollout status deployment/api-gateway  -n banking --timeout=120s

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
  -Body '{"clientId":"rewabank-web","publicClient":true,"redirectUris":["http://localhost:4200/*"],"webOrigins":["http://localhost:4200"],"directAccessGrantsEnabled":true,"standardFlowEnabled":true,"enabled":true}'

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
API Gateway          http://localhost/api/v1                         Istio ingress
Keycloak Admin       http://localhost/auth/admin                     Istio ingress
Keycloak Token       http://localhost/auth/realms/rewabank/          Istio ingress
                     protocol/openid-connect/token
Angular App          http://localhost:4200
Kiali                http://localhost:20001                          istioctl dashboard
Grafana              http://localhost:3000                           istioctl dashboard
Jaeger               http://localhost:16686                          istioctl dashboard
Prometheus           http://localhost:9090                           istioctl dashboard

⚠️ No port-forward needed for API Gateway or Keycloak.
   Use: .\k8s\start-cluster.ps1 to start the cluster.

Cluster Management:
Stop cluster:        k3d cluster stop rewabank
Start cluster:       .\k8s\start-cluster.ps1   ← always use this, not k3d directly
Delete cluster:      k3d cluster delete rewabank
List clusters:       k3d cluster list

Credentials:
Keycloak admin:      admin / admin@2024
Realm:               rewabank
Angular client:      rewabank-web (public)
MS client:           rewabank-ms / rewabank-ms-secret-2024
PostgreSQL:          rewabank@2024
Valkey (Redis):      redis@2024
MongoDB:             mongodb@2024

Internal Service Hostnames:
PostgreSQL:          postgresql-rw.banking.svc.cluster.local:5432
Valkey (Redis):      valkey.banking.svc.cluster.local:6379
Kafka:               kafka-kafka-bootstrap.banking.svc.cluster.local:9092
MongoDB:             mongodb-svc.banking.svc.cluster.local:27017
Keycloak:            keycloak-keycloakx-http.keycloak.svc.cluster.local  ← no :80 (port 80 is default, omitted in JWT iss claim)
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
# Realm/users/clients are persisted in PostgreSQL keycloak_db — they survive restarts
# Only the temporary admin session expires. Re-run setup-keycloak.ps1 if realm is missing:
& ".\k8s\setup-keycloak.ps1"

# All endpoints returning 401 even with valid token
# Root cause: Keycloak issues tokens with iss=http://localhost/auth/... but MS expect internal DNS
# Fix: set Keycloak frontend URL (only needed once — persisted in keycloak_db)
$ADMIN_TOKEN = (Invoke-RestMethod -Uri "http://localhost/auth/realms/master/protocol/openid-connect/token" -Method POST -ContentType "application/x-www-form-urlencoded" -Body "grant_type=password&client_id=admin-cli&username=admin&password=admin@2024").access_token
Invoke-RestMethod -Uri "http://localhost/auth/admin/realms/rewabank" -Method PUT -Headers @{Authorization="Bearer $ADMIN_TOKEN"} -ContentType "application/json" -Body '{"realm":"rewabank","enabled":true,"attributes":{"frontendUrl":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth"}}'
# Then get a fresh token and retry

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

# Gateway rewritePath causing 404 on microservice endpoints
# Root cause: rewritePath strips /api/v1/service/ prefix but MS controller expects full path
# Fix: remove rewritePath filter from the affected route in ApiGatewayApplication.java
# Then rebuild: mvn -q -DskipTests package && docker build/push && kubectl rollout restart deployment/api-gateway -n banking

# OTP verify returning AUTH_500 (unexpected error)
# Root cause: malformed JSON body — $OTP_CODE variable contained a psql error message
# Fix: always insert test OTP using pgcrypto crypt() — never copy-paste bcrypt hashes
kubectl exec -it postgresql-1 -n banking -- psql -U postgres -d auth_db -c "CREATE EXTENSION IF NOT EXISTS pgcrypto; UPDATE otp_records SET used=true WHERE user_id='<USER_ID>'; INSERT INTO otp_records (user_id, otp_hash, purpose, expires_at) VALUES ('<USER_ID>', crypt('123456', gen_salt('bf',10)), 'TRANSFER', NOW() + INTERVAL '5 minutes');"

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
# Insert test OTP via pgcrypto first:
kubectl exec -it postgresql-1 -n banking -- psql -U postgres -d auth_db -c "CREATE EXTENSION IF NOT EXISTS pgcrypto; UPDATE otp_records SET used=true WHERE user_id='<BANKING_USER_ID>'; INSERT INTO otp_records (user_id, otp_hash, purpose, expires_at) VALUES ('<BANKING_USER_ID>', crypt('123456', gen_salt('bf',10)), 'TRANSFER', NOW() + INTERVAL '5 minutes');"
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
$ACCOUNT_ID = $acct.id
$ACCOUNT_NUMBER = $acct.accountNumber
# Expected: 201 Created — status=PENDING (needs TELLER to activate)
# Valid accountTypes: SAVINGS | CURRENT

# Get my accounts
Invoke-RestMethod -Uri "http://localhost/api/v1/accounts/my-accounts" -Headers @{Authorization="Bearer $TOKEN"}
# Expected: array of accounts for current JWT user

# Get account by ID
Invoke-RestMethod -Uri "http://localhost/api/v1/accounts/$ACCOUNT_ID" -Headers @{Authorization="Bearer $TOKEN"}
# Expected: full account details

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
# Expected: paginated list of transactions for current user

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
```

---

# RAM Saving Tips

```powershell
# Stop cluster completely when not using
k3d cluster stop rewabank

# Start again — always use the startup script
.\k8s\start-cluster.ps1

# Scale down replicas while developing
kubectl scale deployment api-gateway --replicas=1 -n banking
kubectl scale deployment auth-ms --replicas=1 -n banking
kubectl scale deployment customers-ms --replicas=1 -n banking
kubectl scale deployment accounts-ms --replicas=1 -n banking
kubectl scale deployment transactions-ms --replicas=1 -n banking
kubectl scale deployment fraud-ms --replicas=1 -n banking
kubectl scale deployment notifications-ms --replicas=1 -n banking
kubectl scale deployment audit-ms --replicas=1 -n banking

# Scale down Istio addons when not needed
kubectl scale deployment kiali --replicas=0 -n istio-system
kubectl scale deployment grafana --replicas=0 -n istio-system
kubectl scale deployment jaeger --replicas=0 -n istio-system

# Scale back up for testing
kubectl scale deployment --all --replicas=1 -n banking
kubectl scale deployment kiali grafana jaeger --replicas=1 -n istio-system

# Scale down services not currently testing
kubectl scale deployment notifications-ms --replicas=0 -n banking
kubectl scale deployment audit-ms --replicas=0 -n banking
```
