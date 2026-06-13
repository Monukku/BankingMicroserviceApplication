# RewaBank cluster startup script
k3d cluster start rewabank
Write-Host "Waiting for cluster..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# Fix nginx port routing
docker exec k3d-rewabank-serverlb sh -c "sed -i 's/agent-0:80/agent-0:31947/g' /etc/nginx/nginx.conf"
docker exec k3d-rewabank-serverlb sh -c "sed -i 's/server-0:80/server-0:31947/g' /etc/nginx/nginx.conf"
docker exec k3d-rewabank-serverlb sh -c "nginx -s reload"

Write-Host "Cluster ready!" -ForegroundColor Green
Write-Host "API Gateway:    http://localhost/api/v1" -ForegroundColor Cyan
Write-Host "Keycloak Admin: http://localhost/auth/admin" -ForegroundColor Cyan
Write-Host "Keycloak Token: http://localhost/auth/realms/rewabank/protocol/openid-connect/token" -ForegroundColor Cyan

# Wait for Keycloak pod
Write-Host "Waiting for Keycloak pod..." -ForegroundColor Yellow
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=keycloakx -n keycloak --timeout=300s

# Wait for Keycloak HTTP
Write-Host "Waiting for Keycloak HTTP..." -ForegroundColor Yellow
$ready = $false
for ($i = 1; $i -le 12; $i++) {
    try {
        $null = Invoke-RestMethod -Uri "http://localhost/auth/realms/master" -Method GET -ErrorAction Stop
        Write-Host "Keycloak HTTP ready!" -ForegroundColor Green
        $ready = $true
        break
    } catch {
        Write-Host "Attempt $i/12 - not ready yet, waiting 10s..." -ForegroundColor Yellow
        Start-Sleep -Seconds 10
    }
}

if ($ready) {
    & "C:\Users\Monukushw\Downloads\Microservices-22-04-2025\k8s\setup-keycloak.ps1"
} else {
    Write-Host "Keycloak took too long. Run setup manually:" -ForegroundColor Red
    Write-Host "Run: .\k8s\setup-keycloak.ps1" -ForegroundColor Yellow
}