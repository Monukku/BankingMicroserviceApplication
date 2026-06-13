$base = "http://localhost/auth"

Write-Host "Getting admin token..." -ForegroundColor Yellow
$TOKEN = (Invoke-RestMethod -Uri "$base/realms/master/protocol/openid-connect/token" -Method POST -ContentType "application/x-www-form-urlencoded" -Body "grant_type=password&client_id=admin-cli&username=admin&password=admin@2024").access_token
if (-not $TOKEN) { Write-Host "Keycloak not ready" -ForegroundColor Red; exit 1 }
Write-Host "Token OK" -ForegroundColor Green

# Create realm
try {
    Invoke-RestMethod -Uri "$base/admin/realms" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body '{"realm":"rewabank","enabled":true,"displayName":"RewaBank"}'
    Write-Host "Realm created!" -ForegroundColor Green
} catch {
    Write-Host "Realm exists" -ForegroundColor Yellow
}

# CRITICAL: Set frontend URL to internal DNS
# Without this JWT tokens have iss=http://localhost/auth/... but all MS expect
# iss=http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth/... causing 401 on every endpoint
Invoke-RestMethod -Uri "$base/admin/realms/rewabank" -Method PUT -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body '{"realm":"rewabank","enabled":true,"attributes":{"frontendUrl":"http://keycloak-keycloakx-http.keycloak.svc.cluster.local/auth"}}'
Write-Host "Frontend URL set to internal DNS!" -ForegroundColor Green

# Refresh token after frontendUrl change (frontendUrl change invalidates the current session)
$TOKEN = (Invoke-RestMethod -Uri "$base/realms/master/protocol/openid-connect/token" -Method POST -ContentType "application/x-www-form-urlencoded" -Body "grant_type=password&client_id=admin-cli&username=admin&password=admin@2024").access_token
Write-Host "Token refreshed after frontendUrl change" -ForegroundColor Green

# Create rewabank-ms client (used by microservices)
try {
    Invoke-RestMethod -Uri "$base/admin/realms/rewabank/clients" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body '{"clientId":"rewabank-ms","secret":"rewabank-ms-secret-2024","redirectUris":["*"],"publicClient":false,"serviceAccountsEnabled":true,"directAccessGrantsEnabled":true,"enabled":true}'
    Write-Host "rewabank-ms client created!" -ForegroundColor Green
} catch {
    Write-Host "rewabank-ms exists" -ForegroundColor Yellow
}

# Create rewabank-web client (used by Angular)
try {
    Invoke-RestMethod -Uri "$base/admin/realms/rewabank/clients" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body '{"clientId":"rewabank-web","publicClient":true,"redirectUris":["http://localhost:4200/*"],"webOrigins":["http://localhost:4200"],"directAccessGrantsEnabled":true,"standardFlowEnabled":true,"enabled":true}'
    Write-Host "rewabank-web client created!" -ForegroundColor Green
} catch {
    Write-Host "rewabank-web exists" -ForegroundColor Yellow
}

# Create roles
$roles = @("CUSTOMER","TELLER","RELATIONSHIP_MANAGER","CREDIT_OFFICER","BRANCH_MANAGER","AUDITOR","SUPER_ADMIN")
foreach ($role in $roles) {
    try {
        Invoke-RestMethod -Uri "$base/admin/realms/rewabank/roles" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body "{`"name`":`"$role`"}"
    } catch {}
}
Write-Host "Roles created!" -ForegroundColor Green

# Grant permissions to rewabank-ms service account
$CLIENT  = (Invoke-RestMethod -Uri "$base/admin/realms/rewabank/clients?clientId=rewabank-ms" -Headers @{Authorization="Bearer $TOKEN"}).id
$SA_USER = (Invoke-RestMethod -Uri "$base/admin/realms/rewabank/clients/$CLIENT/service-account-user" -Headers @{Authorization="Bearer $TOKEN"}).id
$MGMT    = (Invoke-RestMethod -Uri "$base/admin/realms/rewabank/clients?clientId=realm-management" -Headers @{Authorization="Bearer $TOKEN"}).id

$roleNames = @("manage-users","view-users","query-users","manage-realm","manage-clients")
$roleObjs = $roleNames | ForEach-Object { Invoke-RestMethod -Uri "$base/admin/realms/rewabank/clients/$MGMT/roles/$_" -Headers @{Authorization="Bearer $TOKEN"} }
$rolesJson = "[" + (($roleObjs | ForEach-Object { $_ | ConvertTo-Json }) -join ",") + "]"
Invoke-RestMethod -Uri "$base/admin/realms/rewabank/users/$SA_USER/role-mappings/clients/$MGMT" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body $rolesJson
Write-Host "Permissions granted!" -ForegroundColor Green

Write-Host "Keycloak fully configured!" -ForegroundColor Green