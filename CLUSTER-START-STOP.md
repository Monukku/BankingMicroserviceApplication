# RewaBank — Cluster Start / Stop Guide

---

## Stopping the cluster (end of day)

```powershell
k3d cluster stop rewabank
```

You can close Docker Desktop after this. All data is preserved.

---

## Starting the cluster (coming back after a break)

Run these 6 steps in order:

### Step 1 — Start Docker Desktop first
Wait for Docker Desktop to fully start before proceeding.

### Step 2 — Start the cluster
```powershell
.\k8s\start-cluster.ps1
```

### Step 3 — Fix kubeconfig
The API server port changes after every restart. Find it and update kubeconfig:
```powershell
docker ps --filter name=k3d-rewabank-serverlb --format "{{.Ports}}"
# Find the port mapped to 6443 — e.g. 0.0.0.0:59408->6443/tcp  ← use 59408

kubectl config set-cluster k3d-rewabank --server=https://127.0.0.1:<PORT>
kubectl get nodes
# Expected: both nodes Ready
```

### Step 4 — Wait for all pods (~3–5 minutes)
```powershell
kubectl get pods -n banking
# Wait until all 7 services show 2/2 Running
```

Services to watch:
| Pod | Expected |
|---|---|
| api-gateway | 2/2 Running |
| auth-ms | 2/2 Running |
| customers-ms | 2/2 Running |
| accounts-ms | 2/2 Running |
| transactions-ms | 2/2 Running |
| fraud-ms | 2/2 Running |
| notifications-ms | 2/2 Running |

### Step 5 — Fix nginx routing
The Istio NodePort and nginx config reset on every start:
```powershell
# Get the current NodePort
kubectl get svc istio-ingressgateway -n istio-system -o jsonpath='{.spec.ports[?(@.port==80)].nodePort}'
# Example output: 31947  ← use this value below

docker exec k3d-rewabank-serverlb sh -c "sed -i 's/agent-0:80/agent-0:31947/g' /etc/nginx/nginx.conf"
docker exec k3d-rewabank-serverlb sh -c "sed -i 's/server-0:80/server-0:31947/g' /etc/nginx/nginx.conf"
docker exec k3d-rewabank-serverlb sh -c "nginx -s reload"
```

### Step 6 — Verify and get a token
```powershell
# Gateway should return 401 (correct — Istio blocking unauthenticated)
curl.exe -s http://localhost/api/v1/actuator/health

# Get a fresh token
$uri = "http://localhost/auth/realms/rewabank/protocol/openid-connect/token"
$b = "grant_type=password&client_id=rewabank-ms&client_secret=rewabank-ms-secret-2024&username=newuser@rewabank.com&password=Test@1234"
$TOKEN = (Invoke-RestMethod -Uri $uri -Method POST -ContentType "application/x-www-form-urlencoded" -Body $b).access_token
$h = @{Authorization="Bearer $TOKEN"}

# Test protected endpoint
Invoke-RestMethod -Uri "http://localhost/api/v1/customers/me" -Headers $h
```

---

## What survives a stop/start

| Data | Survives? | Notes |
|---|---|---|
| Registered users | ✅ Yes | Stored in PostgreSQL |
| Keycloak realm, clients, roles | ✅ Yes | Stored in PostgreSQL |
| Account and transaction data | ✅ Yes | Stored in PostgreSQL |
| K8s Secrets and ConfigMaps | ✅ Yes | Stored in k3s |
| Redis/Valkey cache | ❌ No | Ephemeral — CQRS read-side rebuilds from events |
| JWT tokens | ❌ No | Always get a fresh token after restart |
| nginx routing | ❌ No | Re-apply Step 5 every time |

---

## Troubleshooting after start

**kubectl can't connect**
```powershell
# Repeat Step 3 with the correct port
docker ps --filter name=k3d-rewabank-serverlb --format "{{.Ports}}"
kubectl config set-cluster k3d-rewabank --server=https://127.0.0.1:<PORT>
```

**http://localhost not working**
```powershell
# Repeat Step 5 — nginx routing reset
```

**Pod in CrashLoopBackOff**
```powershell
kubectl logs deployment/<name> -n banking -c <name> --previous
```

**Keycloak realm missing (rare — only if cluster was deleted)**
```powershell
.\k8s\setup-keycloak.ps1
```

**Full reset (delete everything and start fresh)**
```powershell
k3d cluster delete rewabank
# Then follow README.md from STEP 1
```

---

## Quick reference

```
API Gateway:    http://localhost/api/v1
Keycloak Admin: http://localhost/auth/admin  (admin / admin@2024)
Keycloak Token: http://localhost/auth/realms/rewabank/protocol/openid-connect/token

MS Client:      rewabank-ms / rewabank-ms-secret-2024
Test user:      newuser@rewabank.com / Test@1234
```