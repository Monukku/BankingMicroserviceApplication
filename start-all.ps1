$base = "C:\Users\Monukushw\Downloads\Microservices-22-04-2025"

$services = @(
    "configserver",
    "discoveryserver",
    "accounts",
    "cards",
    "loans",
    "customers",
#    "audit-service",
#    "transaction-service",
#    "verification-service",
#    "notification-service",
    "message",
    "apigateway"
)

foreach ($service in $services) {
    Write-Host "`n🚀 Starting $service..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$base\$service'; mvn spring-boot:run"
    Write-Host "⏳ Waiting 15 seconds for $service to start..." -ForegroundColor Yellow
    Start-Sleep -Seconds 15
}