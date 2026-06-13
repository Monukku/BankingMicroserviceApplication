# RewaBank — CI/CD Pipeline Documentation

---

## Overview

This pipeline follows the enterprise DevSecOps pattern used at banks and fintechs.
Every push to `master`, `main`, `feature/**`, or `fix/**` triggers the pipeline automatically.
You can also trigger it manually from GitHub Actions UI and specify which service to build.

```
Parse_Context → Build → Base_Image_Validation → Unit_Tests_SonarQube
→ Dependency_Scan → SCA_Scan → Mutation_Test → SAST_Scan
→ Build_Docker → Image_Scan → Container_Scan → Generate_Scorecard
→ Publish_Artifacts → Create_Deployment_Package → Tag_Deployment_Artifact
→ Print_Receipt
```

**Free tools used (enterprise equivalents in brackets):**

| Pipeline Stage | Tool Used | Enterprise Equivalent |
|---|---|---|
| Unit Tests | Maven Surefire | Same |
| Code Quality | SonarCloud | SonarQube |
| Dependency CVE scan | OWASP Dependency Check | JFrog Xray |
| SCA (license + CVE) | Snyk | Snyk Enterprise |
| Mutation Testing | PIT (pitest-maven) | Same |
| SAST | CodeQL | Snyk SAST / Checkmarx |
| Base image scan | Trivy | JFrog Xray |
| Image CVE scan | Trivy | JFrog Xray |
| Container scan | Snyk | Snyk Container |
| Repo security posture | OpenSSF Scorecard | Custom |
| Registry | Docker Hub | AWS ECR / Azure ACR |
| Digest pinning | docker inspect sha256 | Same (automated by CI) |

---

## Required GitHub Secrets

Go to: `GitHub repo → Settings → Secrets and variables → Actions → New repository secret`

| Secret | How to get |
|---|---|
| `DOCKERHUB_USERNAME` | Your Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub → Account Settings → Security → New Access Token |
| `SONAR_TOKEN` | sonarcloud.io → My Account → Security → Generate Token |
| `SNYK_TOKEN` | snyk.io → Account Settings → Auth Token |
| `NVD_API_KEY` | nvd.nist.gov → Request API Key → check email |

---

## How to Generate Each Token

---

### 1. DOCKERHUB_USERNAME

This is just your Docker Hub username — no token needed.

1. Go to [hub.docker.com](https://hub.docker.com) and log in
2. Your username is shown top right (e.g. `rewabank`)
3. Add it as secret `DOCKERHUB_USERNAME` with that value

---

### 2. DOCKERHUB_TOKEN

1. Go to [hub.docker.com](https://hub.docker.com) → log in
2. Top right → click your avatar → **Account Settings**
3. Left menu → **Security**
4. Click **New Access Token**
5. Fill in:
   - **Description:** `rewabank-github-actions`
   - **Access permissions:** `Read & Write`
6. Click **Generate**
7. **Copy the token immediately** — it is shown only once
8. Add as secret `DOCKERHUB_TOKEN`

---

### 3. SONAR_TOKEN

1. Go to [sonarcloud.io](https://sonarcloud.io)
2. Click **Log in** → **Log in with GitHub** → authorize
3. Top right → click your avatar → **My Account**
4. Left menu → **Security**
5. Under "Generate Tokens":
   - **Name:** `rewabank`
   - Click **Generate**
6. Copy the token
7. Add as secret `SONAR_TOKEN`

**One-time SonarCloud project setup (do this once):**
1. On SonarCloud → click **+** → **Analyze new project**
2. Select `BankingMicroserviceApplication` from GitHub
3. Create a free organization (name it `monukku`)
4. Follow the setup wizard — choose **GitHub Actions** as the CI method

---

### 4. SNYK_TOKEN

1. Go to [snyk.io](https://snyk.io)
2. Click **Sign up** → **Sign up with GitHub** → authorize (free tier)
3. Complete onboarding (skip the project import — not needed)
4. Top right → click your avatar → **Account Settings**
5. Under **Auth Token** (or "API Token" in newer UI):
   - Click **Generate** or **click to show**
   - If asked for a name: `rewabank-github-actions`
6. Copy the token
7. Add as secret `SNYK_TOKEN`

---

### 5. NVD_API_KEY

Used by OWASP Dependency Check to download CVE data from the National
Vulnerability Database (NVD). Without this key, NVD rate-limits requests
(HTTP 429) when multiple services run in parallel — causing the scan to fail.

1. Go to [nvd.nist.gov/developers/request-an-api-key](https://nvd.nist.gov/developers/request-an-api-key)
2. Fill in:
   - **Organization:** your name or `RewaBank`
   - **Email:** your email address
   - **Reason:** `Security scanning for open source project`
3. Click **Request Key**
4. Check your email — NVD sends the key within a few minutes
5. Copy the API key from the email
6. Add as secret `NVD_API_KEY`

> **Free tier:** NVD API keys are completely free with no usage limits for
> non-commercial use. The key removes rate limiting immediately.

---

### 6. Add All Secrets to GitHub

1. Go to `github.com/Monukku/BankingMicroserviceApplication`
2. Click **Settings** → left menu → **Secrets and variables** → **Actions**
3. Click **New repository secret** for each:

| Name | Value |
|---|---|
| `DOCKERHUB_USERNAME` | your Docker Hub username |
| `DOCKERHUB_TOKEN` | token from step 2 |
| `SONAR_TOKEN` | token from step 3 |
| `SNYK_TOKEN` | token from step 4 |
| `NVD_API_KEY` | key from step 5 |

> **Note:** Secret names must start with a letter and contain only letters,
> numbers, and underscores. No spaces.

---

## Triggers

| Event | What runs |
|---|---|
| Push to `feature/**` or `fix/**` | Jobs 1–8 (build + test + security scans only) |
| Push to `master` / `main` | All 16 jobs including Docker build + publish + deploy |
| Pull Request to `master` / `main` | Jobs 1–8 (gate before merge) |
| Manual (`workflow_dispatch`) | Choose service + optional tag → all 16 jobs |

---

## Manual Trigger (workflow_dispatch)

1. Go to GitHub → **Actions** tab
2. Left sidebar → click **RewaBank CI/CD — Enterprise Pipeline**
3. Click **Run workflow** (top right)
4. Fill in:
   - **Service**: pick one service or `all`
   - **Image tag**: leave blank to use git SHA, or enter a custom tag (e.g. `v1.2.0`)
5. Click **Run workflow**

This is the "pass image" feature — you control exactly which service gets built and with what tag.

---

## Pipeline Stages — Detailed

---

### JOB 1 — Parse_Context

**What it does:**
- Extracts git SHA, branch, actor, trigger type
- Detects which services changed using `dorny/paths-filter`
- Builds a JSON matrix of only the changed services
- If `workflow_dispatch` — uses the service you selected instead

**Output:**
- `matrix` — JSON list of services to build
- `short_sha` — first 7 chars of commit SHA (used as image tag)
- `image_tag` — custom tag if provided, else short_sha
- `has_changes` — true/false (skips all downstream jobs if false)

**Why it matters:**
Only changed services get built — saves CI minutes and avoids rebuilding
services that haven't changed. Same pattern used in enterprise monorepos.

---

### JOB 2 — Build

**What it does:**
- Installs the BOM (`rewabank-bom`) first
- Compiles and packages each changed service (`mvn package -DskipTests`)
- Uploads the JAR as a GitHub artifact for downstream jobs

**Why separate from tests:**
Build failures are caught fast (< 1 min) before waiting for the full test suite.
Downstream jobs (Docker build) download the pre-built JAR instead of rebuilding.

---

### JOB 3 — Base_Image_Validation

**What it does:**
- Pulls `eclipse-temurin:21-jre-alpine` (the base Docker image used in all Dockerfiles)
- Trivy scans it for CRITICAL and HIGH CVEs
- Uploads the scan report as an artifact
- `exit-code: 0` — warns only, does not fail the build (we don't own the base image)

**Runs in parallel with Build** — no dependency needed.

**Why it matters:**
If the base image has a known Critical CVE, you know before you build on top of it.
In enterprise pipelines, a Critical CVE in the base image triggers a Slack alert to
the security team even if it doesn't block the build.

---

### JOB 4 — Unit_Tests_SonarQube

**What it does:**
- Runs `mvn verify` — compiles, tests, generates JaCoCo coverage report
- Uploads results to SonarCloud (free SonarQube equivalent)
- SonarCloud checks the quality gate — fails if coverage drops below threshold,
  or if new bugs/code smells are introduced
- Uploads test reports as artifacts (kept 7 days)

**Required secret:** `SONAR_TOKEN`

**SonarCloud setup (one-time):**
1. Go to sonarcloud.io → Log in with GitHub
2. Click `+` → Analyze new project → import `BankingMicroserviceApplication`
3. Create organization `monukku` (free)
4. Each service gets its own project key: `rewabank-accounts-service`, etc.

---

### JOB 5 — Dependency_Scan

**What it does:**
- Runs OWASP Dependency Check against each service's `pom.xml`
- Checks all Maven dependencies against the NVD (National Vulnerability Database)
- Fails build if any dependency has CVSS score >= 9 (Critical)
- Uploads HTML + JSON reports as artifacts (kept 30 days)

**Enterprise equivalent:** JFrog Xray dependency scan

**Why CVSS >= 9 threshold:**
Banks don't block on every CVE — that would stop every build.
CVSS 9+ means actively exploitable with no authentication required.
That's the threshold that gets you a regulatory finding from RBI auditors.

---

### JOB 6 — SCA_Scan

**What it does:**
- Snyk Software Composition Analysis — checks open-source licenses + CVEs
- Scans `pom.xml` for each service
- Uploads SARIF results to GitHub Security tab
- `continue-on-error: true` — free tier has rate limits; warns but doesn't block

**Required secret:** `SNYK_TOKEN`

**Difference from OWASP:**
OWASP uses NVD database. Snyk uses its own curated database — they catch
different vulnerabilities. Running both = defense in depth (dual-vendor scanning).

---

### JOB 7 — Mutation_Test

**What it does:**
- PIT mutation testing — injects small bugs ("mutations") into the code
  and checks if the test suite catches them
- 60% mutation threshold — if fewer than 60% of mutations are caught, the job
  warns (continue-on-error: true — doesn't block pipeline)
- Uploads HTML + XML mutation reports as artifacts (kept 7 days)

**Runs after Unit_Tests_SonarQube** — needs compiled test classes.

**Why mutation testing matters:**
100% line coverage doesn't mean your tests are good.
A test that calls a method but asserts nothing will pass coverage but fail mutation.
Interviewers love this distinction — it shows you understand test quality vs test quantity.

**Matrix strategy:**
Each service runs mutation tests independently in parallel — same pattern as
the enterprise pipeline screenshot.

---

### JOB 8 — SAST_Scan

**What it does:**
- CodeQL static analysis — scans Java source for security vulnerabilities
- Uses `security-and-quality` query pack — finds injection, deserialization,
  path traversal, XSS, and other OWASP Top 10 issues
- Uploads SARIF to GitHub Security tab → appears under "Code scanning alerts"

**Enterprise equivalent:** Snyk SAST / Checkmarx

**Runs once for the whole repo** — not per service (CodeQL works on the full source tree).

---

### JOB 9 — Build_Docker

**Only runs on master/main or manual dispatch.**

**What it does:**
- Downloads pre-built JAR from Build job (no Maven rerun)
- Builds Docker image tagged `rewabank/{service}:sha-{SHORT_SHA}` (temp tag)
- Pushes to Docker Hub with the SHA tag only — NOT as `:latest` yet
- Uses GitHub Actions cache for Docker layers (speeds up repeat builds)

**Why SHA tag and not :latest yet:**
The image goes through 2 more security scans (Image_Scan + Container_Scan)
before being promoted to `:latest`. Same pattern as enterprise "promote after scan".

**Required secrets:** `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`

---

### JOB 10 — Image_Scan

**What it does:**
- Pulls the SHA-tagged image from Docker Hub
- Trivy scans OS packages + application libraries inside the image
- `exit-code: 1` + `severity: CRITICAL` — blocks pipeline on any Critical CVE
- `ignore-unfixed: true` — ignores CVEs with no fix available (not actionable)
- Uploads SARIF to GitHub Security tab

**Enterprise equivalent:** JFrog Xray Docker image scan

---

### JOB 11 — Container_Scan

**What it does:**
- Snyk container scan — second vendor scan of the same image
- Different CVE database from Trivy = catches different issues
- Uploads SARIF to GitHub Security tab
- `continue-on-error: true` — free tier has limits

**Enterprise equivalent:** Snyk Container

**Why two image scanners:**
JFrog Xray + Snyk is the dual-vendor pattern used at HDFC, Axis, and other
banks. Each vendor misses some CVEs the other catches. This is "defense in depth"
at the image layer — same argument as running two antivirus engines.

---

### JOB 12 — Generate_Scorecard

**What it does:**
- OpenSSF Scorecard measures the security posture of the repo itself
- Checks: branch protection, signed commits, dependency pinning, code review,
  CI/CD security, token permissions, etc.
- Publishes results to OpenSSF public scorecard dashboard
- Uploads SARIF to GitHub Security tab

**Runs independently** — does not block publish.

**Only runs on master/main.**

---

### JOB 13 — Publish_Artifacts

**Only runs on master/main or manual dispatch, after all scans pass.**

**What it does:**

1. **Idempotency check** — checks if this SHA was already published.
   If you re-run the pipeline for the same commit, it won't push a duplicate.

2. **Promote** — pulls `sha-{SHORT_SHA}` tag, retags as `:latest`, pushes `:latest`

3. **Get digest** — runs:
   ```bash
   docker inspect --format='{{index .RepoDigests 0}}' rewabank/{service}:latest
   ```
   Returns: `rewabank/{service}@sha256:abc123...`

4. **Save digest** — writes digest to a file, uploads as GitHub artifact
   for the next job to consume

**This is the "Publish" stage** — image is now officially released.

---

### JOB 14 — Create_Deployment_Package

**What it does:**
- Downloads digest files from Publish_Artifacts
- For each service, updates the k8s manifest:
  ```yaml
  # Before (mutable)
  image: rewabank/customers-service:latest

  # After (immutable — pinned to exact build)
  image: rewabank/customers-service@sha256:abc123...
  ```
- Commits the updated manifests back to the repo with message:
  `chore: pin image digests [sha: abc1234] [skip ci]`

**Why `[skip ci]`:**
The commit message contains `[skip ci]` so the push of manifest changes
doesn't trigger another pipeline run (would cause infinite loop).

**Why this matters for banking:**
Digest pinning means your cluster always runs the exact image that passed
all security scans. Nobody can push a malicious `:latest` and have it
accidentally deployed. This is a hard requirement in PCI-DSS environments.

---

### JOB 15 — Tag_Deployment_Artifact

**What it does:**
- Creates an annotated git tag in format: `v{YYYYMMDD}-{SHORT_SHA}`
  e.g. `v20260613-5eaab49`
- Tag includes: branch, full commit SHA, trigger type, actor name
- Pushes the tag to the remote

**Why it matters:**
The tag is your rollback point. If a deployment goes wrong, ops can:
```bash
git checkout v20260613-5eaab49
kubectl apply -f k8s/apps/
```
and be back on the last known-good version in seconds.

---

### JOB 16 — Print_Receipt

**Always runs regardless of success or failure.**

**What it does:**
- Prints a formatted receipt of every stage result
- Shows overall status: DEPLOYED / BUILD FAILED / SECURITY SCAN FAILED / etc.
- Acts as the audit trail for the pipeline run

**Why it matters for banking:**
RBI and ISO 27001 audits require evidence that every deployment went through
security gates. The receipt (stored in GitHub Actions logs) is that evidence.
Enterprise pipelines call this the "deployment manifest" or "change record".

---

## Image Flow Summary

```
Code push
    ↓
mvn package → JAR
    ↓
docker build → rewabank/{service}:sha-abc1234   ← temp tag (not :latest)
    ↓
Trivy scan + Snyk scan
    ↓  (only if scans pass)
docker tag → rewabank/{service}:latest          ← promoted to latest
    ↓
docker inspect → sha256:xyz789...               ← get immutable digest
    ↓
k8s manifest: image: rewabank/{service}@sha256:xyz789...   ← pinned
    ↓
git commit + push manifest
    ↓
git tag v20260613-abc1234                       ← rollback point
```

---

## After Pipeline Runs — Deploy to Local k3d

The pipeline updates the k8s manifests with pinned digests and commits them.
Pull the latest manifests and apply:

```powershell
git pull origin master
kubectl apply -f k8s/apps/customers-service.yaml
kubectl apply -f k8s/apps/accounts-service.yaml
kubectl apply -f k8s/apps/transactions-service.yaml
kubectl rollout status deployment/customers-ms deployment/accounts-ms deployment/transactions-ms -n banking
```

---

## Why Not Auto-Deploy from GitHub Actions?

GitHub Actions runners cannot reach your local k3d cluster (it's on your laptop,
not a public IP). In a real enterprise setup:
- The cluster is on AWS/Azure → Actions runner can reach it via kubeconfig secret
- Or GitOps (ArgoCD/Flux) watches the repo → pulls changes automatically

For local dev: pull + apply manually after the pipeline completes.

---

## Interview Talking Points

**"Walk me through your CI/CD pipeline"**

> "We follow a 16-stage enterprise DevSecOps pipeline. Build and tests run in parallel
> with a base image scan. We use dual-vendor scanning — OWASP + Snyk for dependencies,
> Trivy + Snyk for container images — because each vendor's CVE database covers different
> vulnerabilities. Docker images are built with a SHA tag first, only promoted to latest
> after all scans pass. After publish, we extract the sha256 digest and pin it in the
> k8s manifest — so the cluster always runs the exact image that passed security gates,
> never a floating :latest. Every run ends with a Print_Receipt job for audit trail,
> which is an RBI compliance requirement."

**"What's the difference between your OWASP scan and Snyk SCA?"**

> "OWASP uses the NVD database, Snyk uses its own curated database. Running both
> gives us defense in depth — Snyk catches CVEs that NVD hasn't indexed yet,
> OWASP catches older CVEs that Snyk may have deprioritised."

**"Why use digest pinning instead of :latest?"**

> "A tag is mutable — someone can push a compromised image to :latest and it gets
> deployed silently on next restart. A sha256 digest is immutable — it's the
> cryptographic hash of the exact image layers. This is a PCI-DSS requirement
> for financial services: every deployed artifact must be traceable to a specific
> build that passed security review."
