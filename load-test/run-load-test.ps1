<#
.SYNOPSIS
    Full automation -- starts cluster if needed, waits for pods, gets token, runs load test.

.EXAMPLE
    .\run-load-test.ps1
    .\run-load-test.ps1 -Users 100 -DurationSeconds 120
    .\run-load-test.ps1 -User "youruser@rewabank.com" -Password "YourPass"
#>

param(
    [string]$User            = "newuser@rewabank.com",   # register a user first if this doesn't exist
    [string]$Password        = "Test@1234",
    [int]   $Users           = 50,
    [int]   $DurationSeconds = 60,

    # --- Per-user token pool (the capacity-test path) ---
    # 0 = legacy single-user mode (all VUs share one bucket).
    # >0 = seed this many users, mint one token each, give every VU its own bucket.
    [int]   $UserPoolSize    = 0,
    [string]$PoolPassword    = "Load@Test1",
    [int]   $ThinkTimeMs     = 200,

    # Reuse the existing roster instead of seeding — NO port-forward, NO gateway/mTLS
    # bypass. Use once the users already exist (they persist across runs).
    [switch]$SkipSeed
)

$ErrorActionPreference = "Stop"

function Write-Step { param($n, $msg) Write-Host "" ; Write-Host "[$n] $msg" -ForegroundColor Cyan   }
function Write-Ok   { param($msg)     Write-Host "    OK   -- $msg"          -ForegroundColor Green  }
function Write-Fail { param($msg)     Write-Host "    FAIL -- $msg"          -ForegroundColor Red    ; exit 1 }
function Write-Wait { param($msg)     Write-Host "    ...  $msg"             -ForegroundColor Yellow }

# ---------------------------------------------------------------------------
Write-Step 1 "Checking Docker Desktop"
try {
    docker info *>$null
    Write-Ok "Docker is running"
} catch {
    Write-Fail "Docker Desktop is not running. Start it first then re-run this script."
}

# ---------------------------------------------------------------------------
Write-Step 2 "Checking k3d cluster"
$clusterStatus = k3d cluster list --no-headers 2>$null | Select-String "rewabank"

if ((-not $clusterStatus) -or ($clusterStatus -match "0/1")) {
    Write-Wait "Cluster not running -- starting now (takes about 2 minutes)..."
    & "$PSScriptRoot\..\k8s\start-cluster.ps1"
    Write-Ok "Cluster started"
} else {
    Write-Ok "Cluster already running"
    $portBindings = docker inspect k3d-rewabank-serverlb --format '{{json .HostConfig.PortBindings}}' | ConvertFrom-Json
    $port = $portBindings.'6443/tcp'[0].HostPort
    kubectl config set-cluster k3d-rewabank --server="https://127.0.0.1:$port" *>$null

    # Re-apply nginx routing fix -- Istio NodePort can change across restarts
    Write-Wait "Re-applying nginx routing fix..."
    $istioPort = kubectl get svc istio-ingressgateway -n istio-system `
                     -o jsonpath='{.spec.ports[?(@.port==80)].nodePort}' 2>$null
    if ($istioPort) {
        $nginxConf = "error_log stderr notice;`n`nworker_processes auto;`nevents {`n  multi_accept on;`n  use epoll;`n  worker_connections 1024;`n}`n`nstream {`n`n  upstream 443_tcp {`n    server k3d-rewabank-agent-0:$istioPort max_fails=1 fail_timeout=10s;`n    server k3d-rewabank-server-0:$istioPort max_fails=1 fail_timeout=10s;`n  }`n`n  server {`n    listen        443;`n    proxy_pass    443_tcp;`n    proxy_timeout 600;`n    proxy_connect_timeout 2s;`n  }`n`n  upstream 6443_tcp {`n    server k3d-rewabank-server-0:6443 max_fails=1 fail_timeout=10s;`n  }`n`n  server {`n    listen        6443;`n    proxy_pass    6443_tcp;`n    proxy_timeout 600;`n    proxy_connect_timeout 2s;`n  }`n`n  upstream 80_tcp {`n    server k3d-rewabank-agent-0:$istioPort max_fails=1 fail_timeout=10s;`n    server k3d-rewabank-server-0:$istioPort max_fails=1 fail_timeout=10s;`n  }`n`n  server {`n    listen        80;`n    proxy_pass    80_tcp;`n    proxy_timeout 600;`n    proxy_connect_timeout 2s;`n  }`n`n}"
        $tmp = [System.IO.Path]::GetTempFileName()
        [System.IO.File]::WriteAllText($tmp, $nginxConf, [System.Text.Encoding]::ASCII)
        docker cp $tmp k3d-rewabank-serverlb:/etc/nginx/nginx.conf
        Remove-Item $tmp
        docker exec k3d-rewabank-serverlb nginx -s reload
        Write-Ok "nginx routing fixed (Istio NodePort: $istioPort)"
    } else {
        Write-Wait "Could not get Istio NodePort -- nginx routing may be stale"
    }
}

# ---------------------------------------------------------------------------
Write-Step 3 "Waiting for all pods in banking namespace to be Ready"
$maxWait   = 300
$waited    = 0
$sleepSecs = 10

while ($waited -lt $maxWait) {
    $notReady = kubectl get pods -n banking --no-headers 2>$null |
                Where-Object { $_ -notmatch "Running|Completed" -and $_.Trim() -ne "" }

    if (-not $notReady) {
        Write-Ok "All pods are Ready"
        break
    }

    Write-Wait "Pods not ready yet ($($waited)s elapsed) -- waiting $($sleepSecs)s..."
    Start-Sleep -Seconds $sleepSecs
    $waited += $sleepSecs
}

if ($waited -ge $maxWait) {
    Write-Host "    Pods still not ready after $($maxWait)s. Current state:" -ForegroundColor Yellow
    kubectl get pods -n banking
    Write-Fail "Timed out waiting for pods. Check: kubectl get pods -n banking"
}

# ---------------------------------------------------------------------------
Write-Step 4 "Verifying API Gateway at http://localhost"
$maxGwWait = 60
$gwWaited  = 0

while ($gwWaited -lt $maxGwWait) {
    try {
        # Hit a protected endpoint -- Istio returns 401 for unauthenticated requests.
        # Any response (200 or 401) means nginx -> Istio -> gateway is working.
        $resp = Invoke-WebRequest -Uri "http://localhost/api/v1/accounts/my-accounts" `
                                  -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        Write-Ok "Gateway is responding (HTTP $($resp.StatusCode))"
        break
    } catch [System.Net.WebException] {
        $code = [int]$_.Exception.Response.StatusCode
        if ($code -eq 401 -or $code -eq 403) {
            Write-Ok "Gateway is responding (HTTP $code -- correct, Istio rejected unauthenticated request)"
            break
        }
    } catch {}
    Write-Wait "Gateway not responding yet ($($gwWaited)s) -- waiting 5s..."
    Start-Sleep -Seconds 5
    $gwWaited += 5
}

if ($gwWaited -ge $maxGwWait) {
    Write-Fail "Gateway not reachable at http://localhost after $($maxGwWait)s"
}

# ---------------------------------------------------------------------------
# Inline ROPC token minter (own try/catch so one failure can't abort the whole run,
# unlike get-token.ps1 which exits on error).
function Get-RopcToken {
    param($User, $Password)
    $body = @{
        grant_type    = "password"
        client_id     = "rewabank-ms"
        client_secret = "rewabank-ms-secret-2024"
        username      = $User
        password      = $Password
    }
    try {
        $r = Invoke-RestMethod -Method Post `
                -Uri "http://localhost/auth/realms/rewabank/protocol/openid-connect/token" `
                -Body $body -ContentType "application/x-www-form-urlencoded" -TimeoutSec 10 -ErrorAction Stop
        return $r.access_token
    } catch { return $null }
}

if ($UserPoolSize -gt 0) {
    # ---- Per-user token pool: each VU gets its own JWT sub -> its own rate-limit bucket ----
    if ($SkipSeed) {
        Write-Step 5 "Reusing existing roster -- NO seeding, NO gateway/mTLS bypass"
        if (-not (Test-Path "$PSScriptRoot\.users.json")) {
            Write-Fail "No roster (.users.json). Run once WITHOUT -SkipSeed to create the users first."
        }
    } else {
        Write-Step 5 "Seeding $UserPoolSize users + minting per-user tokens"
        & "$PSScriptRoot\seed-users.ps1" -Count $UserPoolSize -Password $PoolPassword
    }

    $roster = Get-Content "$PSScriptRoot\.users.json" -Raw | ConvertFrom-Json
    if ($roster -isnot [array]) { $roster = @($roster) }
    if ($roster.Count -gt $UserPoolSize) { $roster = $roster[0..($UserPoolSize - 1)] }  # take first N
    if ($roster.Count -lt $UserPoolSize) {
        Write-Wait "roster has only $($roster.Count) users (< $UserPoolSize requested); run without -SkipSeed to add more"
    }

    $tokens = @()
    foreach ($u in $roster) {
        $t = Get-RopcToken -User $u.email -Password $u.password
        if ($t) { $tokens += $t }
        Start-Sleep -Milliseconds 150   # gentle pacing so token minting can't self-throttle
    }
    if ($tokens.Count -eq 0) { Write-Fail "Could not mint any pool tokens -- check seeding / Keycloak" }
    Write-Ok "$($tokens.Count)/$UserPoolSize tokens minted"
    if ($Users -ne $tokens.Count) {
        Write-Wait "NOTE: $Users VUs sharing $($tokens.Count) tokens (round-robin). For 1 bucket per VU, set -Users = -UserPoolSize."
    }

    Write-Step 6 "Starting load test -- $Users VUs across $($tokens.Count) users for $($DurationSeconds)s"
    Write-Host ""
    & "$PSScriptRoot\load-test.ps1" -Tokens $tokens -Users $Users -DurationSeconds $DurationSeconds -ThinkTimeMs $ThinkTimeMs
}
else {
    # ---- Legacy single-user mode: all VUs share one bucket ----
    Write-Step 5 "Getting JWT token for user: $User"
    $TOKEN = & "$PSScriptRoot\get-token.ps1" -User $User -Password $Password
    if (-not $TOKEN) {
        Write-Fail "Could not get token -- check credentials or cluster state"
    }
    Write-Ok "Token obtained"

    Write-Step 6 "Starting load test -- $Users users for $($DurationSeconds)s"
    Write-Host ""
    & "$PSScriptRoot\load-test.ps1" -Token $TOKEN -Users $Users -DurationSeconds $DurationSeconds -ThinkTimeMs $ThinkTimeMs
}
