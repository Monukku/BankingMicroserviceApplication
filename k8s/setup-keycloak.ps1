$base = "http://localhost/auth"

Write-Host "Getting admin token..." -ForegroundColor Yellow
$KEYCLOAK_ADMIN_PASSWORD = $env:KEYCLOAK_ADMIN_PASSWORD
if (-not $KEYCLOAK_ADMIN_PASSWORD) { $KEYCLOAK_ADMIN_PASSWORD = Read-Host "Enter Keycloak admin password" -AsSecureString | ForEach-Object { [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($_)) } }
$TOKEN = (Invoke-RestMethod -Uri "$base/realms/master/protocol/openid-connect/token" -Method POST -ContentType "application/x-www-form-urlencoded" -Body "grant_type=password&client_id=admin-cli&username=admin&password=$KEYCLOAK_ADMIN_PASSWORD").access_token
if (-not $TOKEN) { Write-Host "Keycloak not ready" -ForegroundColor Red; exit 1 }
Write-Host "Token OK" -ForegroundColor Green

# Create realm (check first — don't assume catch = already exists)
$realmExists = $false
try {
    Invoke-RestMethod -Uri "$base/admin/realms/rewabank" -Headers @{Authorization="Bearer $TOKEN"} | Out-Null
    $realmExists = $true
    Write-Host "Realm already exists" -ForegroundColor Yellow
} catch {}

if (-not $realmExists) {
    try {
        Invoke-RestMethod -Uri "$base/admin/realms" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body '{"realm":"rewabank","enabled":true,"displayName":"RewaBank"}'
        Write-Host "Realm created!" -ForegroundColor Green
    } catch {
        Write-Host "ERROR creating realm: $_" -ForegroundColor Red
        exit 1
    }
}

# Set frontendUrl to the public domain so the iss claim in tokens matches KEYCLOAK_ISSUER_URI on microservices.
# KEYCLOAK_JWKS_URI stays on internal DNS so microservices fetch signing keys without going through ingress.
Invoke-RestMethod -Uri "$base/admin/realms/rewabank" -Method PUT -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body '{"realm":"rewabank","enabled":true,"attributes":{"frontendUrl":"https://api.rewarealm.com/auth"}}'
Write-Host "Frontend URL set to https://api.rewarealm.com/auth" -ForegroundColor Green

# Create rewabank-ms client (used by microservices)
try {
    Invoke-RestMethod -Uri "$base/admin/realms/rewabank/clients" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body '{"clientId":"rewabank-ms","secret":"rewabank-ms-secret-2024","redirectUris":["*"],"publicClient":false,"serviceAccountsEnabled":true,"directAccessGrantsEnabled":true,"enabled":true}'
    Write-Host "rewabank-ms client created!" -ForegroundColor Green
} catch {
    Write-Host "rewabank-ms exists" -ForegroundColor Yellow
}

# Create or update rewabank-web client (used by Angular)
$webClientBody = '{"clientId":"rewabank-web","publicClient":true,"redirectUris":["https://www.rewarealm.com/*","https://angular-banking-app.vercel.app/*","http://localhost:4200/*"],"webOrigins":["https://www.rewarealm.com","https://angular-banking-app.vercel.app","http://localhost:4200"],"attributes":{"post.logout.redirect.uris":"https://www.rewarealm.com/*##https://angular-banking-app.vercel.app/*"},"directAccessGrantsEnabled":true,"standardFlowEnabled":true,"enabled":true}'
$existingWebClient = Invoke-RestMethod -Uri "$base/admin/realms/rewabank/clients?clientId=rewabank-web" -Headers @{Authorization="Bearer $TOKEN"}
if ($existingWebClient.Count -eq 0) {
    Invoke-RestMethod -Uri "$base/admin/realms/rewabank/clients" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body $webClientBody
    Write-Host "rewabank-web client created!" -ForegroundColor Green
} else {
    Invoke-RestMethod -Uri "$base/admin/realms/rewabank/clients/$($existingWebClient[0].id)" -Method PUT -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body $webClientBody
    Write-Host "rewabank-web client updated!" -ForegroundColor Green
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

# Create testuser (idempotent — skip if exists)
try {
    $existingUsers = Invoke-RestMethod -Uri "$base/admin/realms/rewabank/users?username=testuser" -Headers @{Authorization="Bearer $TOKEN"}
    if ($existingUsers.Count -eq 0) {
        Invoke-RestMethod -Uri "$base/admin/realms/rewabank/users" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body '{"username":"testuser","email":"testuser@rewabank.com","firstName":"Test","lastName":"User","enabled":true,"emailVerified":true,"credentials":[{"type":"password","value":"Test@1234","temporary":false}]}'
        Write-Host "testuser created!" -ForegroundColor Green

        # Assign SUPER_ADMIN role to testuser
        $testUser = (Invoke-RestMethod -Uri "$base/admin/realms/rewabank/users?username=testuser" -Headers @{Authorization="Bearer $TOKEN"})[0]
        $superAdminRole = Invoke-RestMethod -Uri "$base/admin/realms/rewabank/roles/SUPER_ADMIN" -Headers @{Authorization="Bearer $TOKEN"}
        Invoke-RestMethod -Uri "$base/admin/realms/rewabank/users/$($testUser.id)/role-mappings/realm" -Method POST -Headers @{Authorization="Bearer $TOKEN"} -ContentType "application/json" -Body "[$(ConvertTo-Json $superAdminRole)]"
        Write-Host "SUPER_ADMIN role assigned to testuser!" -ForegroundColor Green
    } else {
        Write-Host "testuser already exists" -ForegroundColor Yellow
    }
} catch {
    Write-Host "testuser setup skipped: $_" -ForegroundColor Yellow
}

Write-Host "Keycloak fully configured!" -ForegroundColor Green