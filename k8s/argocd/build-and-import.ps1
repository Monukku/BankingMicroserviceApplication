<#
.SYNOPSIS
    Local image loop for k3d: build jar -> build image -> import into k3d -> rollout.
    Argo CD handles CONFIG (manifests) from Git; this handles the IMAGE (Argo assumes a
    registry, which local k3d doesn't need thanks to `k3d image import`).

.DESCRIPTION
    Replaces the manual build/push/rmi/prune/restart loop. No Docker Hub round-trip:
    `k3d image import` loads the image straight into the cluster's containerd.
    REQUIREMENT: the deployment's imagePullPolicy must be IfNotPresent (not Always) in
    k8s/apps so k3d uses the imported image instead of trying to pull :latest from Docker Hub.

.EXAMPLE
    .\build-and-import.ps1 -Service api-gateway
    .\build-and-import.ps1 -Service accounts-service
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Service,
    [string]$Cluster = "rewabank",
    [switch]$SkipBuild        # just re-import + rollout an already-built image
)

$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot)   # repo root (k8s/argocd -> k8s -> root)

# service dir -> deployment name (from the CI catalog)
$deployMap = @{
    "accounts-service"      = "accounts-ms"
    "api-gateway"           = "api-gateway"
    "auth-service"          = "auth-ms"
    "cards-service"         = "cards-ms"
    "customers-service"     = "customers-ms"
    "fraud-service"         = "fraud-ms"
    "loans-service"         = "loans-ms"
    "notifications-service" = "notifications-ms"
    "transactions-service"  = "transactions-ms"
    "payments-service"      = "payments-ms"
}
if (-not $deployMap.ContainsKey($Service)) { throw "Unknown service '$Service'. One of: $($deployMap.Keys -join ', ')" }
$deploy = $deployMap[$Service]
$image  = "rewabank/$Service:latest"
$dir    = Join-Path $root $Service

function Step($m){ Write-Host "`n[$Service] $m" -ForegroundColor Cyan }

if (-not $SkipBuild) {
    Step "1. mvn package (skip tests)"
    & mvn -q -f (Join-Path $dir "pom.xml") package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "maven build failed" }

    Step "2. docker build -> $image"
    docker build -t $image $dir
    if ($LASTEXITCODE -ne 0) { throw "docker build failed" }
}

Step "3. k3d image import (no registry push needed)"
k3d image import $image -c $Cluster

Step "4. rollout restart deploy/$deploy"
kubectl rollout restart deploy/$deploy -n banking
kubectl rollout status  deploy/$deploy -n banking --timeout=120s

Write-Host "`n    DONE -- $image live in '$Cluster' (deploy/$deploy)." -ForegroundColor Green
Write-Host "    If the pod didn't pick up the new image, set imagePullPolicy: IfNotPresent in k8s/apps/$Service.yaml." -ForegroundColor DarkYellow