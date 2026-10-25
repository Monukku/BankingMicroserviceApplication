# RewaBank cluster startup script
k3d cluster start rewabank
# After cluster start, always sync kubeconfig (port may differ across restarts)
k3d kubeconfig merge rewabank --kubeconfig-merge-default | Out-Null
# Update server URL to 127.0.0.1 (avoids host.docker.internal resolution issues on Windows)
$port = (docker inspect k3d-rewabank-serverlb --format '{{json .HostConfig.PortBindings}}' | ConvertFrom-Json).'6443/tcp'[0].HostPort
kubectl config set-cluster k3d-rewabank --server="https://127.0.0.1:$port" | Out-Null
Write-Host "Waiting for cluster..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# Fix nginx port routing — write correct config explicitly to avoid sed touching server-0:6443 (k3s API)
$ISTIO_PORT = kubectl get svc istio-ingressgateway -n istio-system -o jsonpath='{.spec.ports[?(@.port==80)].nodePort}'
Write-Host "Istio NodePort: $ISTIO_PORT" -ForegroundColor Cyan
$nginxConf = "error_log stderr notice;`n`nworker_processes auto;`nevents {`n  multi_accept on;`n  use epoll;`n  worker_connections 1024;`n}`n`nstream {`n`n  upstream 443_tcp {`n    server k3d-rewabank-agent-0:$ISTIO_PORT max_fails=1 fail_timeout=10s;`n    server k3d-rewabank-server-0:$ISTIO_PORT max_fails=1 fail_timeout=10s;`n  }`n`n  server {`n    listen        443;`n    proxy_pass    443_tcp;`n    proxy_timeout 600;`n    proxy_connect_timeout 2s;`n  }`n`n  upstream 6443_tcp {`n    server k3d-rewabank-server-0:6443 max_fails=1 fail_timeout=10s;`n  }`n`n  server {`n    listen        6443;`n    proxy_pass    6443_tcp;`n    proxy_timeout 600;`n    proxy_connect_timeout 2s;`n  }`n`n  upstream 80_tcp {`n    server k3d-rewabank-agent-0:$ISTIO_PORT max_fails=1 fail_timeout=10s;`n    server k3d-rewabank-server-0:$ISTIO_PORT max_fails=1 fail_timeout=10s;`n  }`n`n  server {`n    listen        80;`n    proxy_pass    80_tcp;`n    proxy_timeout 600;`n    proxy_connect_timeout 2s;`n  }`n`n}"
$tmpNginx = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($tmpNginx, $nginxConf, [System.Text.Encoding]::ASCII)
docker cp $tmpNginx k3d-rewabank-serverlb:/etc/nginx/nginx.conf
Remove-Item $tmpNginx
docker exec k3d-rewabank-serverlb nginx -s reload

Write-Host "Cluster ready!" -ForegroundColor Green
Write-Host "API Gateway:    https://api.rewarealm.com/api/v1" -ForegroundColor Cyan
Write-Host "Keycloak Admin: https://api.rewarealm.com/auth/admin" -ForegroundColor Cyan
Write-Host "Keycloak Token: https://api.rewarealm.com/auth/realms/rewabank/protocol/openid-connect/token" -ForegroundColor Cyan
Write-Host "Frontend:       https://angular-banking-app.vercel.app" -ForegroundColor Cyan
Write-Host "(Cloudflare Tunnel must be running: cloudflared tunnel run rewabank)" -ForegroundColor Yellow

# Wait for Keycloak to start by polling directly inside the pod (bypasses Istio ingress delay)
Write-Host "Waiting for Keycloak to start (up to 10 min)..." -ForegroundColor Yellow
$ready = $false
for ($i = 1; $i -le 20; $i++) {
    $result = kubectl exec keycloak-keycloakx-0 -n keycloak -c keycloak -- sh -c "grep -c 1F90 /proc/net/tcp6; echo done" 2>&1
    if ($result -match "^[1-9]") {
        Write-Host "Keycloak ready!" -ForegroundColor Green
        $ready = $true
        break
    }
    Write-Host "Attempt $i/20 - not ready yet, waiting 30s..." -ForegroundColor Yellow
    Start-Sleep -Seconds 30
}

if ($ready) {
    Write-Host "Waiting 30s for Istio ingress to register Keycloak..." -ForegroundColor Yellow
    Start-Sleep -Seconds 30
    $env:KEYCLOAK_ADMIN_PASSWORD = kubectl get secret keycloak-admin-secret -n keycloak -o jsonpath='{.data.password}' | ForEach-Object { [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_)) }
    & "C:\Users\Monukushw\Downloads\FullStackProject\Microservices-22-04-2025\k8s\setup-keycloak.ps1"
} else {
    Write-Host "Keycloak took too long. Run setup manually:" -ForegroundColor Red
    Write-Host "Run: .\k8s\setup-keycloak.ps1" -ForegroundColor Yellow
}