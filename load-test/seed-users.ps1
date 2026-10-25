<#
.SYNOPSIS
    Seeds N load-test users via auth-service /register (idempotent) and writes a roster JSON.

.DESCRIPTION
    Port-forwards straight to auth-ms, which bypasses BOTH the gateway's 10/min register
    rate limit AND Istio mTLS STRICT (port-forward tunnels to 127.0.0.1 inside the pod,
    which the Envoy sidecar does not intercept). Each user gets a unique email + Indian
    mobile. Re-runnable: users that already exist (HTTP 409 AUTH_001) are kept in the roster.
    The roster feeds per-user token minting in run-load-test.ps1 so every virtual user gets
    its own JWT `sub` -> its own gateway rate-limit bucket (the whole point of the rework).

.EXAMPLE
    .\seed-users.ps1 -Count 50
#>
param(
    [int]    $Count       = 10,
    [string] $Password    = "Load@Test1",      # meets policy: upper+lower+digit+special
    [string] $EmailPrefix = "loadtest",
    [string] $EmailDomain = "rewabank.com",
    [string] $Namespace   = "banking",
    [string] $Deployment  = "auth-ms",
    [int]    $LocalPort   = 18073,              # uncommon port to avoid clashes
    [string] $OutFile     = "$PSScriptRoot\.users.json"
)

$ErrorActionPreference = "Stop"
function Ok   ($m) { Write-Host "    OK   -- $m" -ForegroundColor Green  }
function Warn ($m) { Write-Host "    WARN -- $m" -ForegroundColor Yellow }

Write-Host ""
Write-Host "[seed] Registering $Count users via $Deployment (port-forward ${LocalPort} -> 8073, bypasses gateway + mTLS)" -ForegroundColor Cyan

# 1. Start port-forward to auth-ms
$pf = Start-Process -FilePath "kubectl" `
        -ArgumentList @("port-forward", "deploy/$Deployment", "${LocalPort}:8073", "-n", $Namespace) `
        -PassThru -WindowStyle Hidden

try {
    # 2. Wait until the tunnel serves (ANY HTTP response — even 401/404 — means it is up)
    $base  = "http://localhost:$LocalPort"
    $ready = $false
    for ($w = 0; $w -lt 30; $w++) {
        Start-Sleep -Milliseconds 500
        try {
            Invoke-WebRequest -Uri "$base/actuator/health" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop | Out-Null
            $ready = $true; break
        } catch {
            if ($_.Exception.Response) { $ready = $true; break }   # got an HTTP status => tunnel up
        }
    }
    if (-not $ready) { throw "port-forward to $Deployment did not become ready on $LocalPort" }
    Ok "port-forward ready"

    # 3. Register users (idempotent — 409 means already seeded)
    $roster  = New-Object System.Collections.Generic.List[object]
    $created = 0; $existing = 0; $failed = 0
    for ($i = 0; $i -lt $Count; $i++) {
        $email   = "$EmailPrefix$i@$EmailDomain"
        $mobile  = "+91-" + (9000000000 + $i)       # 10 digits after +91, unique per i
        $payload = @{ fullName = "Load Test $i"; email = $email; mobileNumber = $mobile; password = $Password } |
                   ConvertTo-Json -Compress
        try {
            Invoke-RestMethod -Uri "$base/api/v1/auth/register" -Method Post -Body $payload `
                -ContentType "application/json" -TimeoutSec 20 -ErrorAction Stop | Out-Null
            $created++
            $roster.Add([pscustomobject]@{ email = $email; password = $Password })
        } catch {
            $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            if ($code -eq 409) {
                $existing++
                $roster.Add([pscustomobject]@{ email = $email; password = $Password })
            } else {
                $failed++
                Warn "register $email failed (HTTP $code)"
            }
        }
    }

    # 4. Persist the roster
    $roster | ConvertTo-Json -Depth 3 | Out-File -FilePath $OutFile -Encoding utf8
    Ok "roster: $OutFile  ($($roster.Count) users -- created=$created existing=$existing failed=$failed)"
    if ($roster.Count -eq 0) { throw "no usable users in roster" }
}
finally {
    if ($pf -and -not $pf.HasExited) { Stop-Process -Id $pf.Id -Force -ErrorAction SilentlyContinue }
}