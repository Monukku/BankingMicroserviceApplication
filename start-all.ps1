$base = "C:\Users\Monukushw\Downloads\Microservices-22-04-2025"

$services = @(
    "accounts-service",
    "cards-service",
    "loans-service",
    "customers-service",
    "audit-service",
    "transactions-service",
    "auth-service",
    "fraud-service",
    "notifications-service",
    "api-gateway"
)

foreach ($service in $services) {
    Write-Host "`n🚀 Starting $service..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$base\$service'; mvn spring-boot:run"
    Write-Host "⏳ Waiting 15 seconds for $service to start..." -ForegroundColor Yellow
    Start-Sleep -Seconds 15
}