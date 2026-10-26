<#
.SYNOPSIS
    Installs Argo CD into the k3d cluster and registers the RewaBank Applications.

.DESCRIPTION
    One-time setup. After this, Argo CD continuously syncs the manifests under k8s/apps
    and k8s/istio (from the Git branch you point it at) into the banking namespace — so a
    pushed manifest change auto-deploys. Run with Docker Desktop + the k3d cluster up.

.EXAMPLE
    .\install-argocd.ps1
    .\install-argocd.ps1 -Branch feature/microservices-restructure
#>
param(
    [string]$Branch     = "master",
    [string]$ArgoVersion = "stable",     # or a pinned tag like v2.13.2
    [int]   $UiPort      = 8090           # local UI: https://localhost:<UiPort>
)

$ErrorActionPreference = "Stop"
function Step($m){ Write-Host "`n[argocd] $m" -ForegroundColor Cyan }
function Ok($m){ Write-Host "    OK   -- $m" -ForegroundColor Green }

Step "1. Create argocd namespace + install Argo CD ($ArgoVersion)"
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd -f "https://raw.githubusercontent.com/argoproj/argo-cd/$ArgoVersion/manifests/install.yaml"

Step "2. Wait for Argo CD server to be ready"
kubectl rollout status deploy/argocd-server -n argocd --timeout=180s
Ok "Argo CD is running"

Step "2b. Configure argocd-server for the /argocd ingress route (insecure + subpath)"
# TLS is terminated at the Cloudflare edge, so argocd-server runs --insecure (plain HTTP),
# and server.rootpath/basehref=/argocd lets it serve under the subpath used by the Istio
# route in k8s/istio/api-gateway.yaml. Without this a reinstall reverts to the upstream
# default (HTTPS-only at /) and the /argocd route breaks with a redirect loop.
kubectl patch configmap argocd-cmd-params-cm -n argocd --type merge -p '{"data":{"server.insecure":"true","server.rootpath":"/argocd","server.basehref":"/argocd"}}'
kubectl rollout restart deploy/argocd-server -n argocd
kubectl rollout status deploy/argocd-server -n argocd --timeout=120s
Ok "argocd-server serving plain HTTP under /argocd -> http://localhost/argocd (or https://api.rewarealm.com/argocd via Cloudflare)"

Step "3. Register the RewaBank Applications (branch: $Branch)"
# Point both Applications at the requested branch, then apply them.
(Get-Content "$PSScriptRoot\rewabank-apps.yaml")  -replace 'targetRevision:.*', "targetRevision: $Branch" | kubectl apply -f -
(Get-Content "$PSScriptRoot\rewabank-istio.yaml") -replace 'targetRevision:.*', "targetRevision: $Branch" | kubectl apply -f -
Ok "Applications registered"

Step "4. Admin password + UI access"
$pw = kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}"
$pw = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($pw))
Write-Host "    user     : admin" -ForegroundColor Yellow
Write-Host "    password : $pw" -ForegroundColor Yellow
Write-Host "    UI       : http://localhost/argocd   (permanent Istio route, no port-forward)" -ForegroundColor Yellow
Write-Host "               or https://api.rewarealm.com/argocd  (external, via Cloudflare Tunnel)" -ForegroundColor Yellow
Write-Host "               fallback: kubectl port-forward svc/argocd-server -n argocd ${UiPort}:443 -> https://localhost:$UiPort" -ForegroundColor DarkGray
Write-Host ""
Write-Host "    NOTE: Argo syncs from GitHub. Push '$Branch' so Argo can see your manifests:" -ForegroundColor DarkYellow
Write-Host "          git push -u origin $Branch" -ForegroundColor DarkYellow