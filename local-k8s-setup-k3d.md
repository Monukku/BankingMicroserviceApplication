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
Step 9  — Deploy all 8 MS
Step 10 — Verify + test
Step 11 — Angular setup
```

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
# Create or update WSL2 config file

@"
[wsl2]
memory=10GB
processors=4
swap=2GB
"@ | Set-Content C:\Users\$env:USERNAME\.wslconfig

# Apply changes
wsl --shutdown

# Restart Docker Desktop after wsl shutdown
```

## 1.4 Install k3d
```powershell
# PowerShell as Administrator

# Option A — winget (easiest)
winget install k3d

# Option B — chocolatey
choco install k3d

# Verify
k3d version
```

## 1.5 Install kubectl (if not already installed)
```powershell
winget install Kubernetes.kubectl

# Verify
kubectl version --client
```

## 1.6 Create k3d cluster
```powershell
# Create rewabank cluster
# --agents 1       = 1 worker node
# --port 80:80     = map port 80 for Istio ingress
# --port 443:443   = map port 443 for HTTPS
# --k3s-arg        = disable traefik (we use Istio instead)

k3d cluster create rewabank `
  --agents 1 `
  --port "80:80@loadbalancer" `
  --port "443:443@loadbalancer" `
  --k3s-arg "--disable=traefik@server:0" `
  --k3s-arg "--disable=servicelb@server:0"

# Wait for cluster to be ready (30 seconds)
kubectl get nodes

# Expected:
# NAME                    STATUS   ROLES
# k3d-rewabank-server-0   Ready    control-plane
# k3d-rewabank-agent-0    Ready    <none>
```

## 1.7 Verify k3d cluster working
```powershell
kubectl get nodes

# Expected:
# NAME                    STATUS   ROLES           AGE
# k3d-rewabank-server-0   Ready    control-plane   30s
# k3d-rewabank-agent-0    Ready    <none>          20s
```

## 1.8 Useful k3d cluster management commands
```powershell
# Stop cluster (saves RAM when not using)
k3d cluster stop rewabank

# Start cluster again
k3d cluster start rewabank

# Delete cluster completely and start fresh
k3d cluster delete rewabank
k3d cluster create rewabank --agents 1 --port "80:80@loadbalancer" --port "443:443@loadbalancer" --k3s-arg "--disable=traefik@server:0" --k3s-arg "--disable=servicelb@server:0"

# List all clusters
k3d cluster list
```

---

# STEP 2 — Install istioctl on Windows

```powershell
# PowerShell as Administrator

# Option A — winget (easiest)
winget install Istio.istioctl

# Option B — manual download
# Go to: https://github.com/istio/istio/releases/tag/1.20.0
# Download: istio-1.20.0-win.zip
# Extract to: C:\istio
# Add C:\istio\bin to Windows PATH:
#   System Properties → Environment Variables
#   → Path → New → C:\istio\bin

# Verify
istioctl version
```

---

# STEP 3 — Install Istio

```powershell
# Install Istio with demo profile
# demo = lighter than default, right for 16GB laptop

istioctl install --set profile=demo -y

# Wait 3-5 minutes

# Verify Istio pods running
kubectl get pods -n istio-system

# Expected:
# istiod-xxx              1/1   Running
# istio-ingressgateway-xxx 1/1  Running
# istio-egressgateway-xxx  1/1  Running

# Install observability addons
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/kiali.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/prometheus.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/grafana.yaml
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/jaeger.yaml

# Wait for addons
kubectl wait --for=condition=ready pod -l app=kiali -n istio-system --timeout=120s
kubectl wait --for=condition=ready pod -l app=grafana -n istio-system --timeout=120s

echo "Istio ready"
```

---

# STEP 4 — Namespaces + Sidecar Injection

```powershell
# Create namespaces
kubectl create namespace banking
kubectl create namespace keycloak
kubectl create namespace monitoring

# Enable Istio sidecar injection on banking namespace
# Every pod gets Envoy sidecar automatically
kubectl label namespace banking istio-injection=enabled

# Verify
kubectl get namespace banking --show-labels
# Should show: istio-injection=enabled

# Apply global mTLS STRICT
# Save as C:\k8s\mtls.yaml first, then apply
# ─────────────────────────────────────────
# apiVersion: security.istio.io/v1beta1
# kind: PeerAuthentication
# metadata:
#   name: banking-mtls-strict
#   namespace: banking
# spec:
#   mtls:
#     mode: STRICT
# ─────────────────────────────────────────

kubectl apply -f C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\mtls-strict.yaml

echo "Namespaces ready"
```

---

# STEP 5 — Add Helm Repos

```powershell
# Install Helm on Windows
winget install Helm.Helm

# Verify
helm version

# NOTE: Bitnami charts are no longer free after Sept 2025 (Broadcom paywall)
# Using official vendor-backed / CNCF charts instead — all 100% free, no auth needed

# Remove bitnami if previously added
helm repo remove bitnami 2>$null

# Add official free repos
helm repo add cnpg        https://cloudnative-pg.github.io/charts
helm repo add strimzi     https://strimzi.io/charts/
helm repo add mongodb     https://mongodb.github.io/helm-charts
helm repo add codecentric https://codecentric.github.io/helm-charts

helm repo update

# Verify
helm repo list
# Expected:
# NAME          URL
# cnpg          https://cloudnative-pg.github.io/charts
# strimzi       https://strimzi.io/charts/
# mongodb       https://mongodb.github.io/helm-charts
# codecentric   https://codecentric.github.io/helm-charts

echo "Helm repos ready"
```

---

# STEP 6 — Deploy Infrastructure

## 6.1 PostgreSQL (CloudNativePG — CNCF)

```powershell
# Install CNPG operator first
helm install cnpg cnpg/cloudnative-pg --namespace cnpg-system --create-namespace

kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=cloudnative-pg -n cnpg-system --timeout=120s

# Create postgres secret
kubectl create secret generic postgresql-secret --namespace banking --from-literal=username=postgres --from-literal=password=rewabank@2024

# Save postgres-cluster.yaml to C:\k8s\postgres-cluster.yaml
# ─────────────────────────────────────────
# apiVersion: postgresql.cnpg.io/v1
# kind: Cluster
# metadata:
#   name: postgresql
#   namespace: banking
# spec:
#   instances: 1
#   storage:
#     size: 5Gi
#   resources:
#     requests:
#       memory: 256Mi
#       cpu: 200m
#     limits:
#       memory: 512Mi
#       cpu: 500m
#   bootstrap:
#     initdb:
#       database: app
#       owner: app
#       secret:
#         name: postgresql-secret
# ─────────────────────────────────────────

kubectl apply -f C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\postgres-cluster.yaml

kubectl wait --for=condition=ready pod -l cnpg.io/cluster=postgresql -n banking --timeout=300s

# Create all databases (one at a time — CREATE DATABASE cannot run in transaction block)
$PG_POD = kubectl get pod -n banking -l cnpg.io/cluster=postgresql -o jsonpath='{.items[0].metadata.name}'

kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE auth_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE customers_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE accounts_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE transactions_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE fraud_db;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE DATABASE audit_db;"

# Create users + grants (can run together)
kubectl exec -it $PG_POD -n banking -- psql -U postgres -c "CREATE USER auth_user WITH PASSWORD 'auth@2024'; CREATE USER customers_user WITH PASSWORD 'customers@2024'; CREATE USER accounts_user WITH PASSWORD 'accounts@2024'; CREATE USER transactions_user WITH PASSWORD 'transactions@2024'; CREATE USER fraud_user WITH PASSWORD 'fraud@2024'; CREATE USER audit_user WITH PASSWORD 'audit@2024'; GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_user; GRANT ALL PRIVILEGES ON DATABASE customers_db TO customers_user; GRANT ALL PRIVILEGES ON DATABASE accounts_db TO accounts_user; GRANT ALL PRIVILEGES ON DATABASE transactions_db TO transactions_user; GRANT ALL PRIVILEGES ON DATABASE fraud_db TO fraud_user; GRANT ALL PRIVILEGES ON DATABASE audit_db TO audit_user;"

# Grant schema permissions (required for PostgreSQL 15+)
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d auth_db -c "GRANT ALL ON SCHEMA public TO auth_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d customers_db -c "GRANT ALL ON SCHEMA public TO customers_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d accounts_db -c "GRANT ALL ON SCHEMA public TO accounts_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d transactions_db -c "GRANT ALL ON SCHEMA public TO transactions_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d fraud_db -c "GRANT ALL ON SCHEMA public TO fraud_user;"
kubectl exec -it $PG_POD -n banking -- psql -U postgres -d audit_db -c "GRANT ALL ON SCHEMA public TO audit_user;"

echo "PostgreSQL ready"
# NOTE: Service name is 'postgresql-rw' (not 'postgresql')
# Use: postgresql-rw.banking.svc.cluster.local:5432
```

## 6.2 Redis → Valkey (Linux Foundation — Redis fork, drop-in compatible)

```powershell
# Save valkey.yaml to C:\k8s\valkey.yaml then apply
# ─────────────────────────────────────────
# apiVersion: apps/v1
# kind: Deployment
# metadata:
#   name: valkey
#   namespace: banking
# spec:
#   replicas: 1
#   selector:
#     matchLabels:
#       app: valkey
#   template:
#     metadata:
#       labels:
#         app: valkey
#     spec:
#       containers:
#       - name: valkey
#         image: valkey/valkey:8
#         ports:
#         - containerPort: 6379
#         args: ["--requirepass", "redis@2024"]
#         resources:
#           requests:
#             memory: 128Mi
#             cpu: 100m
#           limits:
#             memory: 256Mi
# ---
# apiVersion: v1
# kind: Service
# metadata:
#   name: valkey
#   namespace: banking
# spec:
#   selector:
#     app: valkey
#   ports:
#   - port: 6379
#     targetPort: 6379
# ─────────────────────────────────────────

kubectl apply -f C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\valkey.yaml

kubectl wait --for=condition=ready pod -l app=valkey -n banking --timeout=180s

echo "Valkey (Redis) ready"
# NOTE: Same port 6379, same password redis@2024, same Redis commands
# Service name changed from 'redis-master' to 'valkey'
# Use: valkey.banking.svc.cluster.local:6379
```

## 6.3 Kafka (Strimzi — CNCF)

```powershell
# Install Strimzi operator
helm install strimzi strimzi/strimzi-kafka-operator --namespace banking --set watchNamespaces="{banking}"

kubectl wait --for=condition=ready pod -l name=strimzi-cluster-operator -n banking --timeout=120s

# Save kafka-cluster.yaml to C:\k8s\kafka-cluster.yaml then apply
# ─────────────────────────────────────────
# apiVersion: kafka.strimzi.io/v1beta2
# kind: KafkaNodePool
# metadata:
#   name: dual-role
#   namespace: banking
#   labels:
#     strimzi.io/cluster: kafka
# spec:
#   replicas: 1
#   roles:
#     - controller
#     - broker
#   storage:
#     type: ephemeral
#   resources:
#     requests:
#       memory: 512Mi
#       cpu: 250m
#     limits:
#       memory: 1Gi
# ---
# apiVersion: kafka.strimzi.io/v1beta2
# kind: Kafka
# metadata:
#   name: kafka
#   namespace: banking
#   annotations:
#     strimzi.io/node-pools: enabled
#     strimzi.io/kraft: enabled
# spec:
#   kafka:
#     version: 4.1.0
#     metadataVersion: "4.1-IV0"
#     listeners:
#       - name: plain
#         port: 9092
#         type: internal
#         tls: false
#     config:
#       offsets.topic.replication.factor: 1
#       transaction.state.log.replication.factor: 1
#       transaction.state.log.min.isr: 1
#   entityOperator:
#     topicOperator: {}
#     userOperator: {}
# ─────────────────────────────────────────

kubectl apply -f C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\kafka-cluster.yaml

kubectl wait kafka/kafka --for=condition=Ready --timeout=300s -n banking

echo "Kafka ready"
# NOTE: Bootstrap server changed from 'kafka:9092' to 'kafka-kafka-bootstrap:9092'
# Use: kafka-kafka-bootstrap.banking.svc.cluster.local:9092
```

## 6.4 MongoDB (MongoDB Community Operator — MongoDB Inc.)

```powershell
# Install MongoDB community operator
helm install mongodb-operator mongodb/community-operator --namespace banking

kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=mongodb-kubernetes-operator -n banking --timeout=120s

# Create mongodb secret
kubectl create secret generic mongodb-secret --namespace banking --from-literal=password=mongodb@2024

# Save mongodb-cluster.yaml to C:\k8s\mongodb-cluster.yaml then apply
# ─────────────────────────────────────────
# apiVersion: mongodbcommunity.mongodb.com/v1
# kind: MongoDBCommunity
# metadata:
#   name: mongodb
#   namespace: banking
# spec:
#   members: 1
#   type: ReplicaSet
#   version: "7.0.0"
#   security:
#     authentication:
#       modes: ["SCRAM"]
#   users:
#     - name: root
#       db: admin
#       passwordSecretRef:
#         name: mongodb-secret
#       roles:
#         - name: clusterAdmin
#           db: admin
#         - name: dbAdminAnyDatabase
#           db: admin
#       scramCredentialsSecretName: mongodb-scram
# ─────────────────────────────────────────

kubectl apply -f C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\mongodb-cluster.yaml

kubectl wait --for=condition=ready pod -l app=mongodb-svc -n banking --timeout=300s

echo "MongoDB ready"
# NOTE: Service name changed from 'mongodb' to 'mongodb-svc'
# Use: mongodb-svc.banking.svc.cluster.local:27017
```

## 6.5 Keycloak (codecentric/keycloakx — open source, uses official Keycloak image)

```powershell
# Save keycloak-values.yaml to C:\k8s\keycloak-values.yaml
# ─────────────────────────────────────────
# command:
#   - "/opt/keycloak/bin/kc.sh"
#   - "start-dev"
# extraEnv: |
#   - name: KEYCLOAK_ADMIN
#     value: admin
#   - name: KEYCLOAK_ADMIN_PASSWORD
#     value: "admin@2024"
# resources:
#   requests:
#     memory: 512Mi
#     cpu: 250m
#   limits:
#     memory: 1Gi
# ─────────────────────────────────────────

helm install keycloak codecentric/keycloakx --namespace keycloak --values C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\keycloak-values.yaml

kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=keycloakx -n keycloak --timeout=300s

echo "Keycloak ready"
# NOTE: Service name changed from 'keycloak' to 'keycloak-keycloakx-http'
# Use: keycloak-keycloakx-http.keycloak.svc.cluster.local:80
# Keycloak uses /auth prefix: http://localhost:8090/auth/admin
```

## 6.6 Configure Keycloak Realm

```powershell
# IMPORTANT: Run port-forward in a SEPARATE Terminal 1 and keep it open
# Terminal 1:
kubectl port-forward svc/keycloak-keycloakx-http -n keycloak 8090:80

# Wait until you see: Forwarding from 127.0.0.1:8090 -> 8080
# Then open Terminal 2 and run the commands below
```

```powershell
# Terminal 2 — Run all config commands here

Start-Sleep -Seconds 5

# Get admin token
# NOTE: Keycloak uses /auth prefix with codecentric chart
$TOKEN = (Invoke-RestMethod `
  -Uri "http://localhost:8090/auth/realms/master/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=admin-cli&username=admin&password=admin@2024"
).access_token

# Create rewabank realm
Invoke-RestMethod `
  -Uri "http://localhost:8090/auth/admin/realms" `
  -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"} `
  -ContentType "application/json" `
  -Body '{"realm":"rewabank","enabled":true,"displayName":"RewaBank"}'

# Create rewabank-ms client
Invoke-RestMethod `
  -Uri "http://localhost:8090/auth/admin/realms/rewabank/clients" `
  -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"} `
  -ContentType "application/json" `
  -Body '{"clientId":"rewabank-ms","secret":"rewabank-ms-secret-2024","redirectUris":["*"],"publicClient":false,"serviceAccountsEnabled":true,"directAccessGrantsEnabled":true,"enabled":true}'

# Create roles
$roles = @("CUSTOMER","TELLER","RELATIONSHIP_MANAGER","CREDIT_OFFICER","BRANCH_MANAGER","AUDITOR","SUPER_ADMIN")
foreach ($role in $roles) {
  Invoke-RestMethod `
    -Uri "http://localhost:8090/auth/admin/realms/rewabank/roles" `
    -Method POST `
    -Headers @{Authorization="Bearer $TOKEN"} `
    -ContentType "application/json" `
    -Body "{`"name`":`"$role`"}"
}

echo "Keycloak realm configured"
```

## 6.7 Verify all infra

```powershell
kubectl get pods -n banking
kubectl get pods -n keycloak
kubectl get pods -n cnpg-system

# All should show Running
# Note: pods show 2/2 because Envoy sidecar injected
```

---

# STEP 7 — K8s Secrets

```powershell
# Run each on a single line — no backtick continuations needed

# PostgreSQL secrets
kubectl create secret generic auth-db-secret --namespace banking --from-literal=username=auth_user --from-literal=password=auth@2024
kubectl create secret generic customers-db-secret --namespace banking --from-literal=username=customers_user --from-literal=password=customers@2024
kubectl create secret generic accounts-db-secret --namespace banking --from-literal=username=accounts_user --from-literal=password=accounts@2024
kubectl create secret generic transactions-db-secret --namespace banking --from-literal=username=transactions_user --from-literal=password=transactions@2024
kubectl create secret generic fraud-db-secret --namespace banking --from-literal=username=fraud_user --from-literal=password=fraud@2024
kubectl create secret generic audit-db-secret --namespace banking --from-literal=username=audit_user --from-literal=password=audit@2024

# Valkey (Redis)
kubectl create secret generic redis-secret --namespace banking --from-literal=password=redis@2024

# Keycloak MS client
kubectl create secret generic keycloak-ms-secret --namespace banking --from-literal=client-id=rewabank-ms --from-literal=client-secret=rewabank-ms-secret-2024

# MongoDB
kubectl create secret generic mongodb-secret --namespace banking --from-literal=password=mongodb@2024

# AES-256 encryption key
$aesKey = [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))
kubectl create secret generic encryption-secret --namespace banking --from-literal=aes-key=$aesKey

# MinIO (for Customers MS docs)
kubectl create secret generic minio-secret --namespace banking --from-literal=access-key=rewabank-minio --from-literal=secret-key=minio@2024

echo "All secrets created"
kubectl get secrets -n banking
```

---

# STEP 8 — Build Docker Images Locally

```powershell
# Run from your project root:
# C:\Users\Monukushw\Downloads\Microservices-22-04-2025\

# Set your Docker Hub username
$DOCKERHUB = "YOUR_DOCKERHUB_USERNAME"
$root = "C:\Users\Monukushw\Downloads\Microservices-22-04-2025"

# Install parent BOM first (required once)
cd $root\rewabank-bom
mvn install -q
cd $root

# Build JAR + Docker image + push + delete local (one at a time to save disk)

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

# STEP 9 — Update K8s Manifests

## 9.1 Update image names in all deployment.yaml files

```powershell
# Replace YOUR_DOCKERHUB in all deployment.yaml files
# Run from project root

$DOCKERHUB = "YOUR_DOCKERHUB_USERNAME"
$root = "C:\Users\Monukushw\Downloads\Microservices-22-04-2025"

Get-ChildItem -Recurse -Filter "deployment.yaml" -Path $root | Where-Object { $_.FullName -notlike "*helm*" } | ForEach-Object {
  (Get-Content $_.FullName) -replace "YOUR_DOCKERHUB", $DOCKERHUB | Set-Content $_.FullName
}

echo "Image names updated in all deployment.yaml files"
```

## 9.2 Update ConfigMaps for local K8s DNS

```powershell
# On k3d the service names are:
# postgresql → postgresql-rw.banking.svc.cluster.local        (CNPG read-write)
# redis/valkey → valkey.banking.svc.cluster.local             (Valkey, same port 6379)
# kafka      → kafka-kafka-bootstrap.banking.svc.cluster.local (Strimzi bootstrap)
# mongodb    → mongodb-svc.banking.svc.cluster.local          (MongoDB community operator)
# keycloak   → keycloak-keycloakx-http.keycloak.svc.cluster.local (codecentric)

# Verify actual service names
kubectl get svc -n banking
kubectl get svc -n keycloak
```

---

# STEP 10 — Deploy All MS

```powershell
# From your project root
$root = "C:\Users\Monukushw\Downloads\Microservices-22-04-2025"
cd $root

# Deploy in dependency order — ONE AT A TIME to save RAM
# Wait for each to be 2/2 Running before deploying next

# 1. ServiceAccounts first
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

# 4. Deployments + Services (one at a time!)
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
### shortcut for deployment

```powershell
kubectl apply -f "C:\Users\Monukushw\Downloads\Microservices-22-04-2025\accounts\k8s\"
kubectl apply -f "C:\Users\Monukushw\Downloads\Microservices-22-04-2025\cards\k8s\"
kubectl apply -f "C:\Users\Monukushw\Downloads\Microservices-22-04-2025\loans\k8s\"
kubectl apply -f "C:\Users\Monukushw\Downloads\Microservices-22-04-2025\customers\k8s\"

```
## Rebuild and redeploy in one script:

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

# STEP 11 — Istio Ingress Gateway

```powershell
# Save as C:\k8s\istio-gateway.yaml then apply
# ─────────────────────────────────────────────
# apiVersion: networking.istio.io/v1beta1
# kind: Gateway
# metadata:
#   name: rewabank-gateway
#   namespace: banking
# spec:
#   selector:
#     istio: ingressgateway
#   servers:
#     - port:
#         number: 80
#         name: http
#         protocol: HTTP
#       hosts:
#         - "*"
# ---
# apiVersion: networking.istio.io/v1beta1
# kind: VirtualService
# metadata:
#   name: rewabank-ingress
#   namespace: banking
# spec:
#   hosts:
#     - "*"
#   gateways:
#     - rewabank-gateway
#   http:
#     - match:
#         - uri:
#             prefix: /api/v1
#       route:
#         - destination:
#             host: api-gateway-service.banking.svc.cluster.local
#             port:
#               number: 8072
#     - match:
#         - uri:
#             prefix: /realms
#       route:
#         - destination:
#             host: keycloak-keycloakx-http.keycloak.svc.cluster.local
#             port:
#               number: 80
# ─────────────────────────────────────────────

kubectl apply -f C:\k8s\istio-gateway.yaml

# On k3d port 80 is directly mapped to localhost
# No NodePort needed — k3d maps 80:80 at cluster creation

kubectl get svc istio-ingressgateway -n istio-system

echo "API accessible at: http://localhost/api/v1"
```

---

# STEP 12 — Verify Everything

```powershell
# Check all pods — should be 2/2 (app + Envoy sidecar)
kubectl get pods -n banking

# Expected output:
# NAME                              READY   STATUS
# api-gateway-xxx                   2/2     Running
# auth-ms-xxx                       2/2     Running
# customers-ms-xxx                  2/2     Running
# accounts-ms-xxx                   2/2     Running
# fraud-ms-xxx                      2/2     Running
# transactions-ms-xxx               2/2     Running
# notifications-ms-xxx              2/2     Running
# audit-ms-xxx                      2/2     Running
# postgresql-1                      2/2     Running
# valkey-xxx                        2/2     Running
# kafka-dual-role-0                 2/2     Running
# mongodb-0                         3/3     Running

# Check services
kubectl get svc -n banking

# Test API Gateway health
curl http://localhost/api/v1/actuator/health

# Test register endpoint
curl -X POST http://localhost/api/v1/auth/register `
  -H "Content-Type: application/json" `
  -d '{
    "fullName": "Test User",
    "email": "test@rewabank.com",
    "mobileNumber": "+919876543210",
    "password": "Test@1234"
  }'
```

---

# STEP 13 — Observability Dashboards

```powershell
# Open each in separate PowerShell terminal

# Kiali — live service mesh + mTLS topology
istioctl dashboard kiali
# Opens browser automatically at http://localhost:20001

# Grafana — metrics
istioctl dashboard grafana
# Opens at http://localhost:3000

# Jaeger — distributed tracing
istioctl dashboard jaeger
# Opens at http://localhost:16686

# Prometheus — raw metrics
istioctl dashboard prometheus
# Opens at http://localhost:9090

# Keycloak admin
kubectl port-forward svc/keycloak-keycloakx-http -n keycloak 8090:80
# http://localhost:8090/admin
# admin / admin@2024
```

---

# STEP 14 — Angular Setup

## 14.1 Install Angular

```powershell
# Install Node.js first
# https://nodejs.org — download LTS version

# Install Angular CLI
npm install -g @angular/cli

# Create RewaBank Angular app
ng new rewabank-web --routing --style=scss
cd rewabank-web

# Install Keycloak adapter
npm install keycloak-js
npm install keycloak-angular
```

## 14.2 Keycloak adapter setup

```typescript
// src/app/app.module.ts
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { KeycloakAngularModule, KeycloakService } from 'keycloak-angular';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';

function initializeKeycloak(keycloak: KeycloakService) {
  return () =>
    keycloak.init({
      config: {
        url: 'http://localhost:8090',           // port-forwarded Keycloak
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
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    KeycloakAngularModule,
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: initializeKeycloak,
      multi: true,
      deps: [KeycloakService],
    },
  ],
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

  // Istio Ingress Gateway — all traffic goes through here
  private readonly BASE_URL = 'http://localhost/api/v1';

  constructor(
    private http: HttpClient,
    private keycloak: KeycloakService
  ) {}

  private async getHeaders(): Promise<HttpHeaders> {
    const token = await this.keycloak.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    });
  }

  // Register
  register(data: any): Observable<any> {
    return this.http.post(`${this.BASE_URL}/auth/register`, data);
  }

  // Get my accounts
  async getMyAccounts(): Promise<Observable<any>> {
    const headers = await this.getHeaders();
    return this.http.get(`${this.BASE_URL}/accounts/my-accounts`, { headers });
  }

  // Create account
  async createAccount(data: any, customerId: string): Promise<Observable<any>> {
    const headers = (await this.getHeaders())
      .set('X-Customer-Id', customerId);
    return this.http.post(`${this.BASE_URL}/accounts`, data, { headers });
  }

  // Transfer money
  async transfer(data: any, idempotencyKey: string): Promise<Observable<any>> {
    const headers = (await this.getHeaders())
      .set('X-Idempotency-Key', idempotencyKey);
    return this.http.post(`${this.BASE_URL}/transactions/transfer`, data, { headers });
  }

  // Get balance
  async getBalance(accountNumber: string): Promise<Observable<any>> {
    const headers = await this.getHeaders();
    return this.http.get(
      `${this.BASE_URL}/accounts/${accountNumber}/balance`,
      { headers }
    );
  }
}
```

## 14.4 Create rewabank-web Keycloak client

```powershell
# Terminal 1 — keep running
kubectl port-forward svc/keycloak-keycloakx-http -n keycloak 8090:80

# Terminal 2 — run config
$TOKEN = (Invoke-RestMethod `
  -Uri "http://localhost:8090/auth/realms/master/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=admin-cli&username=admin&password=admin@2024"
).access_token

# Create public Angular client
Invoke-RestMethod `
  -Uri "http://localhost:8090/auth/admin/realms/rewabank/clients" `
  -Method POST `
  -Headers @{Authorization="Bearer $TOKEN"} `
  -ContentType "application/json" `
  -Body '{"clientId":"rewabank-web","publicClient":true,"redirectUris":["http://localhost:4200/*"],"webOrigins":["http://localhost:4200"],"directAccessGrantsEnabled":true,"standardFlowEnabled":true,"enabled":true}'

echo "Angular Keycloak client created"
```

## 14.5 Run Angular

```powershell
# From rewabank-web folder
ng serve

# App runs at: http://localhost:4200
# Connects to API Gateway at: http://localhost/api/v1
# Auth via Keycloak at: http://localhost:8090
```

---

# Quick Reference — Local URLs

```
Service              URL
──────────────────────────────────────────────────────
API Gateway          http://localhost/api/v1
Keycloak Admin       http://localhost:8090/admin
Angular App          http://localhost:4200
Kiali                http://localhost:20001
Grafana              http://localhost:3000
Jaeger               http://localhost:16686
Prometheus           http://localhost:9090

k3d Cluster Management:
Stop cluster:        k3d cluster stop rewabank
Start cluster:       k3d cluster start rewabank
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

Internal Service Hostnames (updated — no longer Bitnami):
PostgreSQL:          postgresql-rw.banking.svc.cluster.local:5432
Valkey (Redis):      valkey.banking.svc.cluster.local:6379
Kafka:               kafka-kafka-bootstrap.banking.svc.cluster.local:9092
MongoDB:             mongodb-svc.banking.svc.cluster.local:27017
Keycloak:            keycloak-keycloakx-http.keycloak.svc.cluster.local:80
```

---

# Troubleshooting

```powershell
# Pod not starting — check logs
kubectl logs -f deployment/auth-ms -n banking

# Pod showing 1/2 instead of 2/2
# Envoy sidecar not injected — check namespace label
kubectl get namespace banking --show-labels

# ImagePullBackOff
# Image name wrong or Docker Hub private
# Make sure image name matches YOUR_DOCKERHUB in deployment.yaml

# CrashLoopBackOff
kubectl logs deployment/auth-ms -n banking --previous
# Usually: wrong DB URL or missing secret

# Check ConfigMap values
kubectl get configmap auth-ms-config -n banking -o yaml

# Check secret exists
kubectl get secret auth-db-secret -n banking

# mTLS connection refused
# Check AuthorizationPolicy
kubectl get authorizationpolicy -n banking

# Restart a deployment
kubectl rollout restart deployment/auth-ms -n banking

# Watch pods starting
kubectl get pods -n banking -w

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
# Then re-run from Step 3 (Istio install)
```

---

# RAM Saving Tips

```powershell
# k3d uses much less RAM than Docker Desktop K8s
# But if RAM is still tight:

# Stop cluster completely when not using (saves all RAM)
k3d cluster stop rewabank

# Start again when needed (30 seconds)
k3d cluster start rewabank

# Scale down replicas to 1 while developing
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

# Scale down services you are not currently testing
kubectl scale deployment notifications-ms --replicas=0 -n banking
kubectl scale deployment audit-ms --replicas=0 -n banking
```
