# Argo CD + k3d — local GitOps

Automates the **deploy** half of your loop. Argo CD runs in the cluster, watches this
repo's `k8s/apps` and `k8s/istio` on a branch, and continuously syncs them into the
`banking` namespace. Push a manifest change → it deploys itself. No more manual
`kubectl apply` / `rollout`.

## The split (important)
Argo CD is **registry/Git oriented**, k3d is local, so the two halves are handled differently:

| Change type | How it deploys | Tool |
|---|---|---|
| **Config** (timeouts, replicas, env, Istio VS/DR, ConfigMaps) | commit + push → Argo syncs | **Argo CD** (automatic) |
| **Code / new image** (local) | build jar → image → load into k3d → rollout | **`build-and-import.ps1`** |

Argo does **not** rebuild images. On real envs the CI pipeline (`.github/workflows/ci.yml`)
builds+pushes to Docker Hub and pins the digest in `k8s/apps` — Argo then deploys that.
Locally there's no registry, so `k3d image import` loads images directly.

## One-time install
```powershell
# Docker Desktop + the k3d 'rewabank' cluster must be up
.\k8s\argocd\install-argocd.ps1 -Branch feature/microservices-restructure
```
It installs Argo CD, registers the two Applications, and prints the admin password.

Open the UI:
```powershell
kubectl port-forward svc/argocd-server -n argocd 8090:443
# https://localhost:8090   (user: admin — password printed by the installer)
```

## ⚠️ Argo reads GitHub, not your laptop
Argo syncs the branch from `github.com/Monukku/BankingMicroserviceApplication`. Your
manifest changes must be **pushed** for Argo to see them:
```powershell
git push -u origin feature/microservices-restructure
```
(Use any branch; pass it to `-Branch`. For a stable demo, point it at `master`.)

## Daily workflow
- **Changed a YAML/config** → commit + push → Argo auto-syncs within ~3 min (or click *Sync* in the UI).
- **Changed code** → `.\k8s\argocd\build-and-import.ps1 -Service api-gateway`
  (builds, imports into k3d, rolls the deployment).

## Required for the image loop to work locally
Set `imagePullPolicy: IfNotPresent` on the deployments in `k8s/apps/*.yaml` (currently
`Always`, which forces a Docker Hub pull and ignores the imported image). Prod wants
`Always`; keep both via a Kustomize overlay later if needed. Because Argo **self-heals**,
change this in the Git manifest — a live `kubectl patch` will be reverted by Argo.

## Safety notes
- Applications start with `prune: false` (won't auto-delete). Flip to `true` in
  `rewabank-apps.yaml` once you trust the sync scope.
- `selfHeal: true` reverts manual drift — expected for GitOps; disable temporarily if you
  need to hand-hack the cluster.
- Infra operators (Postgres/Kafka/Valkey/Mongo) are **not** managed by Argo here — they're
  bootstrapped once by `k8s/start-cluster.ps1`. Argo only owns `k8s/apps` + `k8s/istio`.

## Next step toward full prod-parity (optional)
Add a **k3d local registry** (`k3d registry create`) so images flow build → push →
Argo-synced manifest tag, exactly like the CI pipeline — then even image updates are pure
GitOps. Until then, `build-and-import.ps1` covers local image updates.