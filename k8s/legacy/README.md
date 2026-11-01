# Legacy in-cluster manifests

These manifests ran the databases **inside** the k3d/K8s cluster. They are no longer
part of the standard deploy — the project moved to cloud-managed databases:

- **PostgreSQL → [Neon](https://neon.tech)** — per-service `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` Secrets
- **MongoDB → [Atlas](https://www.mongodb.com/atlas)** — `MONGODB_URI` Secret (`mongodb-atlas-secret`)

Kept here only for reference or a fully-offline/local-only mode. They are **not** applied by
CI (`.github/workflows/infra.yml`) or the standard setup anymore.

| File | Was | Replaced by |
|------|-----|-------------|
| `postgres-cluster.yaml` | CloudNativePG cluster (`postgresql-rw.banking.svc.cluster.local:5432`) | Neon |
| `mongodb-cluster.yaml`  | Community Operator (`mongodb-svc.banking.svc.cluster.local:27017`)   | Atlas |

To run fully in-cluster again you'd also need the operators (CloudNativePG / MongoDB Community
Operator Helm charts) and would point each service's `DB_URL` / `MONGODB_URI` back at the
in-cluster DNS names above.
