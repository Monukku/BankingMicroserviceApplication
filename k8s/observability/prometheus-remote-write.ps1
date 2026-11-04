<#
.SYNOPSIS
    Re-apply the Grafana Cloud Mimir remote_write + series-cap keep-list onto the
    in-cluster Istio-addon Prometheus (ns istio-system, ConfigMap `prometheus`).

.DESCRIPTION
    This Prometheus is NOT in the repo and NOT Argo-managed, so its ConfigMap is lost
    whenever the addon is re-applied / the cluster is rebuilt. Run this afterwards.

    Why the keep-list: the free tier caps at ~15k active series + 1500 samples/s. The
    control-plane firehose this Prometheus scrapes (apiserver/etcd/workqueue histograms)
    is ~96k series and gets Mimir-429'd → blank dashboards. The keep-list ships only the
    app + istio request series (~6.8k), well under cap.

    Idempotent: strips any existing remote_write first, then appends a fresh block.
    Token is a runtime param so it never lands in git.

    GOTCHAS: the pod's app container is named `prometheus-server` (NOT `prometheus`) —
    `kubectl logs/exec -c prometheus` silently returns empty. After applying, Mimir may
    keep 429'ing for ~10-20 min while the old ~96k series age out of the active window.

.EXAMPLE
    .\prometheus-remote-write.ps1 -Token 'glc_xxx'
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Token,
    [string]$Namespace  = "istio-system",
    [string]$ConfigMap  = "prometheus",
    [string]$Deployment = "prometheus",
    [string]$Username   = "3719075",
    [string]$Url        = "https://prometheus-prod-43-prod-ap-south-1.grafana.net/api/prom/push"
)

$ErrorActionPreference = "Stop"

$keepRegex = '(jvm|process|system|http_server_requests_seconds|kafka|hikaricp|resilience4j|executor|tomcat|logback_events|spring|reactor|disk)_.*|istio_requests_total|istio_request_duration_milliseconds_(bucket|sum|count)|up|scrape_.*'

# 1. Pull the current prometheus.yml and strip any existing top-level remote_write block.
$current = kubectl get cm $ConfigMap -n $Namespace -o jsonpath='{.data.prometheus\.yml}'
if (-not $current) { throw "Could not read prometheus.yml from cm/$ConfigMap in $Namespace" }

$lines  = $current -split "`n"
$out    = New-Object System.Collections.Generic.List[string]
$skip   = $false
foreach ($line in $lines) {
    if ($line -match '^\s*remote_write\s*:') { $skip = $true; continue }
    # A new top-level key (column 0, non-space) ends the remote_write block.
    if ($skip -and $line -match '^\S') { $skip = $false }
    if (-not $skip) { $out.Add($line) }
}

# 2. Append the fresh remote_write block with the keep-list.
$block = @"
remote_write:
  - url: $Url
    basic_auth:
      username: "$Username"
      password: $Token
    write_relabel_configs:
      - source_labels: [__name__]
        regex: $keepRegex
        action: keep
"@
$newConfig = ($out -join "`n").TrimEnd() + "`n" + $block + "`n"

# 3. Merge-patch only the prometheus.yml key (the CM has other keys — don't recreate it).
$patch = "data:`n  prometheus.yml: |`n" + (($newConfig -split "`n") | ForEach-Object { "    $_" } -join "`n")
$tmp = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($tmp, $patch, [System.Text.Encoding]::ASCII)
kubectl patch cm $ConfigMap -n $Namespace --type merge --patch-file $tmp
Remove-Item $tmp

# 4. Deterministic reload.
kubectl rollout restart deploy/$Deployment -n $Namespace
Write-Host "remote_write + keep-list applied. Allow ~10-20m for old series to age out of Mimir's active window." -ForegroundColor Green