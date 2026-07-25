# 7. DevOps Perspective

[← Back to README](../README.md)

Everything above from the app/Vault-mechanics angle; this document is the
same project from a DevOps/Platform engineer's day-to-day angle — what
they'd actually do, in what order, and why.

## Who Owns What

| Concern | Developer | DevOps Engineer | Platform Engineer |
|---|---|---|---|
| Calls `hvac`, decides which secrets the app needs | ✅ | | |
| Writes/reviews the app's Vault policy | | ✅ (often security-reviewed) | ✅ owns the standard |
| Writes real secret values (`vault kv put` in prod) | rarely | ✅ | |
| Sets up AppRole roles / K8s auth roles | | ✅ | ✅ owns the auth backend |
| Runs/upgrades the Vault cluster itself | | sometimes | ✅ |
| Owns audit logging & compliance | | | ✅ |
| Wires Vault into CI/CD pipelines | | ✅ | ✅ provides the reusable pattern |

Small teams wear all three hats (exactly like this demo). Larger orgs
split them: Platform owns "how Vault works" as a paved-road service,
DevOps/SRE owns "how our pipelines and infra consume it," developers
consume secrets through a thin client library platform provides.

## Deploying the Application

Standard container deploy — build, push, roll out (see
[06-deployment.md](06-deployment.md)). The Vault-specific wrinkle is
**credential bootstrapping**: the very first thing a new instance needs
before it can do anything else is a way to authenticate to Vault, and
that credential can't itself come from Vault. DevOps solves this per
platform: Kubernetes auth (identity from the platform itself, nothing to
inject), a CI step that provisions a fresh `secret_id` and injects it as
part of the deploy, or a cloud-identity-based auth method.

## Configuring Vault

Day-1: mount the secrets engine(s), enable auth method(s), define
policies, create roles — all shown command-by-command in
[05-vault-setup.md](05-vault-setup.md). In a real org this is typically
codified as Terraform (the official `vault` Terraform provider) rather
than run by hand, so it's reviewable, repeatable, and auditable the same
way infrastructure is.

## Managing Secrets

- Static values (`kv put`) for things that genuinely have no dynamic
  equivalent.
- Prefer dynamic secrets engines (Database, AWS, PKI) wherever the
  target system supports it — see
  [04-vault-integration.md → Dynamic Secrets](04-vault-integration.md#dynamic-secrets).
- One policy/role per app per environment — never a shared "god" secret.

## Rotating Credentials

`vault/scripts/rotate-secret-demo.sh` in this repo demonstrates the ideal
end state: rotate at the source (`vault kv put`), and every consumer
picks up the new value on its next read/render — no code change, no
coordinated restart. For dynamic secrets, rotation is even more hands-off:
each lease simply expires and gets replaced automatically.

## Monitoring Vault

Vault's own `/v1/sys/health` + Prometheus telemetry, plus the audit
device log stream. Alert on: sealed status changing unexpectedly, a
spike in `403`s (policy misconfiguration or a compromised/misused
credential), unusually high request rates from one identity, and lease
counts trending upward without matching revocations (a leak of
long-lived dynamic credentials).

## Troubleshooting Failures

The systematic order, same one used throughout this repo's
[Troubleshooting sections](04-vault-integration.md#troubleshooting):
1. **Reachability** — `vault status` / this app's Home page "Vault
   Status" card.
2. **Authentication** — `vault token lookup` / this app's "Authentication
   Status" card. Did login even succeed?
3. **Authorization** — does the token's policy actually grant this exact
   path? (`vault token lookup` shows policies; `vault policy read`
   shows what they grant.)
4. **Existence** — does the secret exist at the exact path being read
   (remembering the `secret/data/...` vs `secret/metadata/...` split)?

## Securing the Infrastructure

TLS everywhere, network-restrict Vault's API to trusted networks,
auto-unseal via cloud KMS instead of manual key-share custody, audit
device enabled, least-privilege policies, short TTLs — the full list is
in [05-vault-setup.md → Security Hardening](05-vault-setup.md#security-hardening).

## Automating Deployments / CI/CD Integration

### Vault in CI/CD

A pipeline job needs secrets too (deploy keys, registry passwords)
without a human present. The pattern:
1. The CI platform has its own trusted identity mechanism (GitHub
   Actions OIDC token, GitLab CI JWT, or a CI-scoped AppRole `secret_id`
   stored as a masked CI variable).
2. A pipeline step exchanges that identity for a short-lived Vault token.
3. Subsequent steps fetch exactly what that job needs; the token expires
   naturally with the job.

Result: no long-lived secret sits in CI variable storage waiting to leak
from a fork's PR build — the CI job's *credential to get credentials* is
itself short-lived and scoped to one pipeline run.

### Vault with Kubernetes

See `k8s-examples/deployment-with-vault-injection.yaml` for an annotated
reference manifest (not deployed by this project).

- **Kubernetes auth method** — a pod authenticates using its own
  ServiceAccount JWT. No `role_id`/`secret_id` to distribute; the pod's
  Kubernetes identity *is* its Vault identity.
- **Vault Agent** — a sidecar process handling authentication and token
  renewal automatically, optionally rendering secrets to files inside the
  pod (Consul-Template-style), keeping them current as they rotate —
  zero application code changes needed.
- **Sidecar Injection** — the Vault Agent Injector is a mutating
  admission webhook: annotate a pod spec, and Kubernetes adds the Vault
  Agent container automatically at pod creation. It's the Kubernetes-
  native equivalent of this project's `vault-init` +
  `docker-entrypoint.sh` pattern (a helper writes credentials to a shared
  filesystem location; the app container just reads them).

## Scaling the Application

The app itself is stateless (no session storage, no local cache of
secrets) so it scales horizontally trivially — the only shared-state
concern is Vault-side: many instances authenticating means many
concurrent AppRole logins and many active tokens/leases. Give the AppRole
role generous-enough `secret_id_num_uses` (or `0` for unlimited, as this
project does) if many replicas share one `secret_id`, or better,
provision one `secret_id` per instance via your orchestrator's identity
mechanism (Kubernetes auth sidesteps this entirely — see above).

## Disaster Recovery for Vault

- Vault itself: see [05-vault-setup.md → Backup and Recovery](05-vault-setup.md#backup-and-recovery)
  (Raft snapshots or file-backend backups, plus safely retained unseal/
  recovery keys — a backup without the keys is undecryptable).
- From the application's side: this app already demonstrates graceful
  degradation — if Vault is unreachable, `/health` reports `503` and the
  UI shows a clear "Cannot reach Vault" state rather than crashing; design
  real services the same way so a Vault outage degrades functionality
  predictably instead of taking down everything that ever touched a
  secret.
- Multi-region/HA Vault clusters (Performance Replication on Enterprise,
  or simply multiple independent clusters with app-level failover) are
  how real orgs avoid Vault being a single point of failure at scale —
  out of scope for this learning project, but the right next thing to
  read about once these fundamentals are solid.

## Production Best Practices (Summary)

See [04-vault-integration.md → Security Best Practices](04-vault-integration.md#security-best-practices)
and [05-vault-setup.md → Security Hardening](05-vault-setup.md#security-hardening)
for the full lists — the short version: least privilege everywhere,
short-lived credentials over long-lived ones, dynamic secrets over
static where possible, TLS + network isolation for Vault itself, audit
logging on, and infrastructure-as-code for Vault configuration so it's
reviewable and repeatable.

Next: [08-appendices.md](08-appendices.md) — FAQ, error reference, CLI
cheat sheet, and glossary.
