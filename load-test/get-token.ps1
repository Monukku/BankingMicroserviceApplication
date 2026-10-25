<#
.SYNOPSIS
    Gets a Keycloak JWT token for load testing.

.EXAMPLE
    $TOKEN = .\get-token.ps1 -User "testuser@rewabank.com" -Password "Test@1234"
    .\load-test.ps1 -Token $TOKEN -Users 50
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$User,

    [Parameter(Mandatory = $true)]
    [string]$Password,

    [string]$KeycloakUrl  = "http://localhost/auth",
    [string]$Realm        = "rewabank",
    [string]$ClientId     = "rewabank-ms",
    [string]$ClientSecret = "rewabank-ms-secret-2024"
)

$body = @{
    grant_type = "password"
    client_id  = $ClientId
    username   = $User
    password   = $Password
}
if ($ClientSecret -ne "") { $body.client_secret = $ClientSecret }

try {
    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "$KeycloakUrl/realms/$Realm/protocol/openid-connect/token" `
        -Body $body `
        -ContentType "application/x-www-form-urlencoded"

    Write-Host "Token expires in: $($response.expires_in)s" -ForegroundColor Green
    return $response.access_token
}
catch {
    Write-Host "Failed to get token: $_" -ForegroundColor Red
    Write-Host "Is the cluster running? Try: .\run-load-test.ps1" -ForegroundColor Yellow
    exit 1
}
