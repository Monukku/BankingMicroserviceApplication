<#
.SYNOPSIS
    RewaBank API Load Test -- fires real HTTP requests against your running cluster.

.DESCRIPTION
    Covers all 9 services routed through api-gateway (port 80 via Istio).
    Uses PowerShell runspace pool (.NET HttpClient) -- no extra tools needed.
    Prints a WORKING / BREAKING / RISK report at the end.

.EXAMPLE
    $TOKEN = .\get-token.ps1 -User "newuser@rewabank.com" -Password "Test@1234"
    .\load-test.ps1 -Token $TOKEN -Users 50 -DurationSeconds 60
#>

param(
    # Supply EITHER a single token (legacy, all VUs share one rate-limit bucket)
    # OR a pool of tokens (one JWT `sub` per VU -> one bucket per VU -> real capacity).
    [string]  $Token           = "",
    [string[]]$Tokens          = @(),

    [string]$BaseUrl           = "http://localhost",
    [int]   $Users             = 50,
    [int]   $DurationSeconds   = 60,
    # Per-VU pause between requests. ~200ms lets each VU approach its own 5 req/s bucket
    # without wasting requests on 429s. (Was a hardcoded 1000ms that masked the shared-token bug.)
    [int]   $ThinkTimeMs       = 200,
    [string]$AccountId         = "",
    [string]$SourceAccountId   = "",
    [string]$DestAccountNumber = "",

    # Services that are intentionally not deployed -- their endpoints are skipped
    # so they don't show up as false BREAKING failures and skew the overall verdict.
    [string[]]$DisabledServices = @("loans-service", "cards-service")
)

function Write-Working  { param($m) Write-Host "  [WORKING]  $m" -ForegroundColor Green  }
function Write-Breaking { param($m) Write-Host "  [BREAKING] $m" -ForegroundColor Red    }
function Write-Risk     { param($m) Write-Host "  [RISK]     $m" -ForegroundColor Yellow }

# Resolve the token pool: prefer -Tokens; fall back to the single -Token.
if ($Tokens.Count -eq 0) {
    if ($Token -ne "") { $Tokens = @($Token) }
    else { Write-Host "  ERROR: provide -Token <jwt> or -Tokens <jwt[]>" -ForegroundColor Red; exit 1 }
}

# ExpectedStatus is the primary OK code.
# AcceptAlso covers valid alternates: 403 = role restriction, 404 = no profile/data yet.
$scenarios = @(
    # /actuator/health is NOT routed through Istio (returns 404) -- skipped
    # Unauth rejection -- any response proves gateway is alive; 401 is expected
    [pscustomobject]@{ Name="[Gateway] Unauth fast-reject (401)";   Service="api-gateway";         Method="GET"; Path="/api/v1/accounts/my-accounts";                                                                                                                                                                                                               NeedsAuth=$false; ExpectedStatus=401; AcceptAlso=@();   SlaMs=200;  Body=$null; ExtraHeaders=@{} },
    # Confirmed 200 in manual test
    [pscustomobject]@{ Name="[Accounts] My accounts (Redis cache)"; Service="accounts-service";    Method="GET"; Path="/api/v1/accounts/my-accounts";                                                                                                                                                               NeedsAuth=$true;  ExpectedStatus=200; AcceptAlso=@();   SlaMs=500;  Body=$null; ExtraHeaders=@{} },
    [pscustomobject]@{ Name="[Transactions] My history";            Service="transactions-service";Method="GET"; Path="/api/v1/transactions";                                                                                                                                                                       NeedsAuth=$true;  ExpectedStatus=200; AcceptAlso=@();   SlaMs=500;  Body=$null; ExtraHeaders=@{} },
    [pscustomobject]@{ Name="[Loans] My loans";                     Service="loans-service";       Method="GET"; Path="/api/v1/loans/my-loans";                                                                                                                                                                     NeedsAuth=$true;  ExpectedStatus=200; AcceptAlso=@();   SlaMs=500;  Body=$null; ExtraHeaders=@{} },
    [pscustomobject]@{ Name="[Cards] My cards";                     Service="cards-service";       Method="GET"; Path="/api/v1/cards/my-cards";                                                                                                                                                                     NeedsAuth=$true;  ExpectedStatus=200; AcceptAlso=@();   SlaMs=500;  Body=$null; ExtraHeaders=@{} },
    # NOTE: /api/v1/fraud/score is internal-only (Istio allows it only from transactions-ms-sa),
    # so it ALWAYS 403s through the gateway regardless of JWT role. We test /alerts instead,
    # which the api-gateway SA is allowed to reach and which requires BRANCH_MANAGER/SUPER_ADMIN.
    [pscustomobject]@{ Name="[Fraud] Open alerts";                  Service="fraud-service";       Method="GET"; Path="/api/v1/fraud/alerts";                                                                                                                                                                       NeedsAuth=$true;  ExpectedStatus=200; AcceptAlso=@(403); SlaMs=500;  Body=$null; ExtraHeaders=@{} },
    [pscustomobject]@{ Name="[Payments] My orders";                 Service="payments-service";    Method="GET"; Path="/api/v1/payments/my-orders";                                                                                                                                                                  NeedsAuth=$true;  ExpectedStatus=200; AcceptAlso=@();   SlaMs=500;  Body=$null; ExtraHeaders=@{} },
    # 404 is OK -- user has no customer profile yet (needs full onboarding)
    [pscustomobject]@{ Name="[Customers] My profile";               Service="customers-service";   Method="GET"; Path="/api/v1/customers/me";                                                                                                                                                                       NeedsAuth=$true;  ExpectedStatus=200; AcceptAlso=@(404); SlaMs=500;  Body=$null; ExtraHeaders=@{} },
    [pscustomobject]@{ Name="[Auth] My profile";                    Service="auth-service";        Method="GET"; Path="/api/v1/auth/me";                                                                                                                                                                            NeedsAuth=$true;  ExpectedStatus=200; AcceptAlso=@(404); SlaMs=1000; Body=$null; ExtraHeaders=@{} },
    # 403 is OK -- testuser may not have AUDITOR role
    [pscustomobject]@{ Name="[Audit] Logs by event type";           Service="audit-service";       Method="GET"; Path="/api/v1/audit/event-type/ACCOUNT_CREATED";                                                                                                                                                   NeedsAuth=$true;  ExpectedStatus=200; AcceptAlso=@(403); SlaMs=500;  Body=$null; ExtraHeaders=@{} }
)

if ($AccountId -ne "") {
    $scenarios += [pscustomobject]@{ Name="[Accounts] Balance (CQRS Redis)"; Service="accounts-service"; Method="GET"; Path="/api/v1/accounts/$AccountId/balance"; NeedsAuth=$true; ExpectedStatus=200; SlaMs=200; Body=$null; ExtraHeaders=@{} }
    $scenarios += [pscustomobject]@{ Name="[Audit] Logs by aggregate";       Service="audit-service";    Method="GET"; Path="/api/v1/audit/aggregate/$AccountId";  NeedsAuth=$true; ExpectedStatus=200; SlaMs=500; Body=$null; ExtraHeaders=@{} }
}

if ($SourceAccountId -ne "" -and $DestAccountNumber -ne "") {
    $scenarios += [pscustomobject]@{
        Name="[Transactions] POST transfer"; Service="transactions-service"; Method="POST"; Path="/api/v1/transactions/transfer"
        NeedsAuth=$true; ExpectedStatus=201; SlaMs=10000
        Body=@{ sourceAccountId=$SourceAccountId; sourceAccountNumber="000000000000"; destinationAccountNumber=$DestAccountNumber; amount="10.00"; remarks="load-test" }
        ExtraHeaders=@{ "GENERATE_IDEM_KEY"="true" }
    }
}

# Drop scenarios for services that aren't deployed (default: loans, cards).
# These would otherwise return 503 and be reported as false BREAKING failures.
$skipped = @($scenarios | Where-Object { $DisabledServices -contains $_.Service })
if ($skipped.Count -gt 0) {
    $scenarios = @($scenarios | Where-Object { $DisabledServices -notcontains $_.Service })
}

$results   = [System.Collections.Concurrent.ConcurrentBag[pscustomobject]]::new()
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

$workerScript = {
    param($BaseUrl, $Token, $Scenarios, $DurationSeconds, $Results, $ThinkTimeMs)

    Add-Type -AssemblyName System.Net.Http
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.ServerCertificateCustomValidationCallback = { $true }
    $client  = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [System.TimeSpan]::FromSeconds(15)
    $sw      = [System.Diagnostics.Stopwatch]::new()
    $endTime = [System.DateTime]::UtcNow.AddSeconds($DurationSeconds)

    while ([System.DateTime]::UtcNow -lt $endTime) {
        $s   = $Scenarios | Get-Random
        $url = "$BaseUrl$($s.Path)"
        $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($s.Method), $url)

        if ($s.NeedsAuth) { $req.Headers.Add("Authorization", "Bearer $Token") }
        if ($s.ExtraHeaders["GENERATE_IDEM_KEY"] -eq "true") { $req.Headers.Add("X-Idempotency-Key", [System.Guid]::NewGuid().ToString()) }
        if ($null -ne $s.Body) {
            $json    = $s.Body | ConvertTo-Json -Compress
            $content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")
            $req.Content = $content
        }

        $sw.Restart()
        try {
            $resp = $client.SendAsync($req).GetAwaiter().GetResult()
            $ms   = $sw.ElapsedMilliseconds
            $code = [int]$resp.StatusCode
            # 429 = rate limited (working correctly) -- not a service error
            $isOk = ($code -eq $s.ExpectedStatus) -or ($s.AcceptAlso -contains $code) -or ($code -eq 429)
            $Results.Add([pscustomobject]@{ Name=$s.Name; Service=$s.Service; Status=$code; Ms=$ms; Ok=$isOk; SlaMs=$s.SlaMs; SlaMiss=($ms -gt $s.SlaMs) })
        } catch {
            $Results.Add([pscustomobject]@{ Name=$s.Name; Service=$s.Service; Status=0; Ms=$sw.ElapsedMilliseconds; Ok=$false; SlaMs=$s.SlaMs; SlaMiss=$true })
        }
        # Per-VU think-time. With per-user tokens each VU owns its own 5 req/s bucket,
        # so a short pause (vs the old 1000ms) lets the test actually load the backends.
        if ($ThinkTimeMs -gt 0) { Start-Sleep -Milliseconds $ThinkTimeMs }
    }
    $client.Dispose()
}

Write-Host ""
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  RewaBank API Load Test -- ALL SERVICES" -ForegroundColor Cyan
Write-Host "  Target   : $BaseUrl" -ForegroundColor Cyan
Write-Host "  Users    : $Users concurrent" -ForegroundColor Cyan
Write-Host "  Token pool: $($Tokens.Count) distinct user(s) -- ceiling ~$($Tokens.Count * 5) req/s (5/s per bucket)" -ForegroundColor Cyan
Write-Host "  ThinkTime: ${ThinkTimeMs}ms per VU" -ForegroundColor Cyan
Write-Host "  Duration : $DurationSeconds seconds" -ForegroundColor Cyan
Write-Host "  Endpoints: $($scenarios.Count)" -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host ""
$scenarios | ForEach-Object { Write-Host "  * $($_.Name)" -ForegroundColor Gray }
if ($skipped.Count -gt 0) {
    Write-Host ""
    Write-Host "  Skipped (not deployed): $(( $skipped | ForEach-Object { $_.Name }) -join ', ')" -ForegroundColor DarkGray
}
Write-Host ""
Write-Host "  Running... watch Grafana: http://localhost:3000" -ForegroundColor Gray
Write-Host ""

$pool = [System.Management.Automation.Runspaces.RunspaceFactory]::CreateRunspacePool(1, $Users)
$pool.Open()

$jobs = @()
for ($i = 0; $i -lt $Users; $i++) {
    $ps = [System.Management.Automation.PowerShell]::Create()
    $ps.RunspacePool = $pool
    [void]$ps.AddScript($workerScript)
    [void]$ps.AddArgument($BaseUrl)
    [void]$ps.AddArgument($Tokens[$i % $Tokens.Count])   # one token per VU (round-robin if VUs > pool)
    [void]$ps.AddArgument($scenarios)
    [void]$ps.AddArgument($DurationSeconds)
    [void]$ps.AddArgument($results)
    [void]$ps.AddArgument($ThinkTimeMs)
    $jobs += [pscustomobject]@{ PS=$ps; Handle=$ps.BeginInvoke() }
}

$endTime = [System.DateTime]::UtcNow.AddSeconds($DurationSeconds)
while ([System.DateTime]::UtcNow -lt $endTime) {
    $remaining = [int]($endTime - [System.DateTime]::UtcNow).TotalSeconds
    $pct       = [int](($DurationSeconds - $remaining) / $DurationSeconds * 100)
    Write-Progress -Activity "Load Test" -Status "$($results.Count) requests fired | $remaining s remaining" -PercentComplete $pct
    Start-Sleep -Milliseconds 500
}

$jobs | ForEach-Object { $_.PS.EndInvoke($_.Handle); $_.PS.Dispose() }
$pool.Close()
Write-Progress -Activity "Load Test" -Completed

$totalMs  = $stopwatch.ElapsedMilliseconds
$allItems = $results.ToArray()

if ($allItems.Count -eq 0) {
    Write-Host "  No results -- is the cluster running at $BaseUrl ?" -ForegroundColor Red
    exit 1
}

$tps = [math]::Round($allItems.Count / ($totalMs / 1000), 1)

Write-Host ""
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  RESULTS -- per endpoint" -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host ""

foreach ($group in ($allItems | Group-Object Name | Sort-Object Name)) {
    $items     = $group.Group
    $total     = $items.Count
    $ok        = ($items | Where-Object { $_.Ok   }).Count
    $errors    = $total - $ok
    $slaMisses = ($items | Where-Object { $_.SlaMiss }).Count
    $lats      = $items | Select-Object -ExpandProperty Ms | Sort-Object
    $p50       = $lats[[math]::Floor($lats.Count * 0.50)]
    $p95       = $lats[[math]::Floor($lats.Count * 0.95)]
    $p99       = $lats[[math]::Floor($lats.Count * 0.99)]
    $avg       = [math]::Round(($lats | Measure-Object -Average).Average)
    $sla       = ($items | Select-Object -First 1).SlaMs
    $errPct    = [math]::Round($errors / $total * 100, 1)
    $slaPct    = [math]::Round($slaMisses / $total * 100, 1)

    # Status code breakdown
    $codeBreakdown = $items | Group-Object Status | Sort-Object Name |
                     ForEach-Object { "$($_.Name)=$($_.Count)" }
    $codeStr = $codeBreakdown -join "  "

    Write-Host "  $($group.Name)" -ForegroundColor White
    Write-Host "     req=$total  errors=$errors ($errPct%)  sla-miss=$slaMisses ($slaPct%)"
    Write-Host "     avg=${avg}ms  p50=${p50}ms  p95=${p95}ms  p99=${p99}ms  [SLA: ${sla}ms]"
    Write-Host "     status codes: $codeStr" -ForegroundColor Gray

    if ($errPct -gt 5)          { Write-Breaking "$errPct% error rate -- $($group.Name) is FAILING" }
    elseif ($errPct -gt 1)      { Write-Risk     "$errPct% errors -- approaching circuit breaker threshold" }
    else                        { Write-Working  "error rate $errPct% is acceptable" }

    if ($p99 -gt $sla) {
        if ($group.Name -like "*100ms*") { Write-Breaking "p99=${p99}ms BREAKS fraud 100ms SLA -- fraudCircuitBreaker WILL trip" }
        else                             { Write-Breaking "p99=${p99}ms exceeds ${sla}ms SLA" }
    } elseif ($p95 -gt [int]($sla * 0.8)) {
        Write-Risk "p95=${p95}ms is within 20% of ${sla}ms SLA boundary"
    } else {
        Write-Working "p99=${p99}ms within ${sla}ms SLA"
    }
    Write-Host ""
}

$totalErr    = ($allItems | Where-Object { -not $_.Ok }).Count
$totalErrPct = [math]::Round($totalErr / $allItems.Count * 100, 1)
$totalSla    = ($allItems | Where-Object { $_.SlaMiss }).Count
$totalSlaPct = [math]::Round($totalSla / $allItems.Count * 100, 1)

Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  OVERALL VERDICT  ($Users users / $DurationSeconds s)" -ForegroundColor Cyan
Write-Host "===================================================================" -ForegroundColor Cyan
Write-Host "  Total requests  : $($allItems.Count)"
Write-Host "  Throughput      : $tps req/s"
Write-Host "  Error rate      : $totalErr / $($allItems.Count) ($($totalErrPct)%)"
Write-Host "  SLA misses      : $totalSla / $($allItems.Count) ($($totalSlaPct)%)"
Write-Host ""

if   ($totalErrPct -gt 5)  { Write-Breaking "System BREAKING at $Users users -- $($totalErrPct)% error rate" }
elseif ($totalErrPct -gt 1){ Write-Risk     "$($totalErrPct)% errors -- near threshold, reduce load or scale" }
else                        { Write-Working "System HOLDS at $Users users -- $($totalErrPct)% error rate"     }

if   ($tps -lt 10) { Write-Breaking "Only $tps req/s -- severe bottleneck" }
elseif ($tps -lt 50){ Write-Risk    "$tps req/s -- low throughput for $Users users" }
else                { Write-Working "$tps req/s throughput healthy" }

Write-Host ""
Write-Host "  Debug commands:" -ForegroundColor Gray
Write-Host "  kubectl top pods -n banking" -ForegroundColor Gray
Write-Host "  kubectl logs -n banking deploy/accounts-ms --tail=50" -ForegroundColor Gray
Write-Host "  Grafana  -> http://localhost:3000" -ForegroundColor Gray
Write-Host "  Jaeger   -> http://localhost:16686" -ForegroundColor Gray
Write-Host "  Kiali    -> http://localhost:20001" -ForegroundColor Gray
Write-Host ""
