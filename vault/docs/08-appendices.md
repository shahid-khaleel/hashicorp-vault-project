# 8. Appendices

[← Back to README](../README.md)

## Frequently Asked Questions

**Why does the app support two auth methods (token and AppRole)?**
Token auth is the fastest way to poke at the app locally (you already
have the dev-mode root token). AppRole is what a real service should use
and is this project's default in Docker — see
[04-vault-integration.md](04-vault-integration.md#authentication-methods-used-by-the-application).

**Why does restarting the `vault` container break the app?**
Dev mode stores everything in memory. A restart wipes all secrets,
policies, and the AppRole role entirely. `vault-init` re-provisions
automatically, but the already-running `web` container is still holding
the *old* `role_id`/`secret_id`. Fix: `docker compose restart web`. See
[Common Errors and Fixes](#common-errors-and-fixes) below.

**Is it safe to commit `vault/scripts/*.sh` and the sample secret values
to a public repo?** Yes — every value in `02-create-secrets.sh` is
fake/harmless by design, meant only to be visible on screen for learning.
Never do this with real credentials; that's exactly the anti-pattern this
whole project exists to teach against.

**Why KV v2 instead of KV v1?** v2 adds versioning (so
[secret rotation](04-vault-integration.md#secret-rotation) has visible
history) and soft-delete — strictly more useful for learning, and the
default recommendation for new KV mounts generally.

**Does this project use dynamic secrets?** No — it uses static KV for
simplicity. [04-vault-integration.md → Dynamic Secrets](04-vault-integration.md#dynamic-secrets)
explains the concept and what a production equivalent would look like
(the Database secrets engine, generating unique per-instance DB
credentials).

**Can I point this app at a real, non-Docker Vault server?** Yes — set
`VAULT_ADDR` to its address, provision a policy/AppRole role there using
the same commands in `vault/scripts/`, and set `VAULT_ROLE_ID`/
`VAULT_SECRET_ID` accordingly (see
[02-development-setup.md](02-development-setup.md)).

## Common Errors and Fixes

| Error / Symptom | Cause | Fix |
|---|---|---|
| `Cannot reach Vault at http://vault:8200` | `vault` container not running/healthy, or `VAULT_ADDR` wrong for your context (`vault` hostname only resolves *inside* the Docker network) | `docker compose ps`; use `localhost` instead of `vault` for host-side processes |
| `AppRole login rejected: invalid role or secret ID` | Vault (dev mode, in-memory) restarted since credentials were issued | `docker compose restart web` to reload fresh credentials from the shared volume |
| `Token's policy does not allow reading ...` (`VaultPermissionDeniedError`) | Token valid, but its policy doesn't grant this path | `vault token lookup` (policies) + `vault policy read <name>` (grants) |
| `No secret found at secret/vault-demo/...` (`VaultSecretNotFoundError`) | Wrong path, or secret was deleted/never written | `vault kv list secret/vault-demo/`; re-run `02-create-secrets.sh` |
| `sh: can't open '/vault-config/...'` when running a script via `docker exec` on Windows/Git Bash | Git Bash rewrites leading `/` paths to Windows paths before they reach `docker exec` | Prefix the command with `MSYS_NO_PATHCONV=1` |
| Vault UI/API TLS errors in production | `VAULT_ADDR` uses `https://` but the client doesn't trust Vault's cert | See [06-deployment.md → Connecting the Application to Vault](06-deployment.md#connecting-the-application-to-vault) |
| `vault-init` container exits non-zero; `web` never starts | A provisioning step failed — often because `vault` wasn't actually healthy yet | `docker logs vault-demo-init` for the failing step |
| `gunicorn: command not found` / import errors on Windows | `gunicorn` needs the Unix-only `fcntl` module | Expected — it's a Linux/macOS-only production server; use `python app.py` for local Windows dev |

## Vault CLI Cheat Sheet

```bash
# Status & auth
vault status                                   # sealed? initialized? HA?
vault operator init                            # one-time, non-dev servers only
vault operator unseal                          # run 3x with different keys
vault login <token>
vault token lookup
vault token create -policy=<name> -ttl=1h

# Secrets (KV v2)
vault kv put secret/<path> key=value
vault kv get secret/<path>
vault kv get -field=<key> secret/<path>
vault kv metadata get secret/<path>            # version history
vault kv list secret/<prefix>/

# Policies
vault policy write <name> <file.hcl>
vault policy list
vault policy read <name>

# Auth methods
vault auth enable approle
vault auth list
vault write auth/approle/role/<role> token_policies="<policy>" token_ttl=1h
vault read auth/approle/role/<role>/role-id
vault write -f auth/approle/role/<role>/secret-id
vault write auth/approle/login role_id=<id> secret_id=<id>

# Leases
vault lease revoke <lease_id>
vault lease renew <lease_id>
```

Vault UI: `http://localhost:8200/ui` (this project), token `root`.

## Configuration Reference (Environment Variables)

| Variable | Default (this project) | Meaning |
|---|---|---|
| `APP_NAME` | `Vault Demo App` | Display name shown in the UI |
| `APP_ENV` | `development` | Shown on the Home page; also toggles Flask debug mode |
| `VAULT_ADDR` | `http://vault:8200` | Vault server URL |
| `VAULT_NAMESPACE` | *(empty)* | Vault Enterprise namespace, if applicable |
| `VAULT_AUTH_METHOD` | `approle` | `approle` or `token` |
| `VAULT_TOKEN` | *(empty)* | Used only when `VAULT_AUTH_METHOD=token` |
| `VAULT_ROLE_ID` / `VAULT_SECRET_ID` | *(from shared volume)* | Used only when `VAULT_AUTH_METHOD=approle` |
| `VAULT_MOUNT_POINT` | `secret` | KV v2 mount path |
| `VAULT_SECRET_PATH` | `vault-demo` | Prefix under the mount where this app's secrets live |

## Reference Architecture Diagram

See [01-project-overview.md](01-project-overview.md#architecture) for the
full component diagram, and
[01-project-overview.md → Data Flow](01-project-overview.md#data-flow-loading-the-secrets-page)
for the request sequence diagram. The byte-level HTTP version is in
[04-vault-integration.md → Complete Request/Response Flow](04-vault-integration.md#complete-requestresponse-flow).

## Glossary

| Term | Meaning |
|---|---|
| **Barrier** | Vault's internal encryption layer between the API and storage backend; everything written to storage passes through it encrypted |
| **Seal / Unseal** | Sealed = Vault can't decrypt its data (no key in memory). Unsealing supplies enough key shares to reconstruct the decryption key |
| **Shamir's Secret Sharing** | The cryptographic scheme splitting Vault's master key into N shares where any threshold M can reconstruct it |
| **Auto-unseal** | Using an external KMS (cloud provider) to unseal automatically instead of humans supplying key shares |
| **Storage Backend** | Where Vault's encrypted data physically lives (file, Raft/Integrated Storage, Consul, etc.) |
| **Secrets Engine** | A plugin mounted at a path that determines what reading/writing there does (KV, Database, PKI, Transit, AWS, ...) |
| **KV (Key-Value) Engine** | The simplest secrets engine — stores and versions whatever you give it (a *static* secret) |
| **Static Secret** | A value a human/script chose ahead of time; Vault stores/versions it but didn't generate it |
| **Dynamic Secret** | A value Vault generates on request, with a lease, later revoked/expired automatically |
| **Auth Method** | How a caller proves identity to Vault (AppRole, Kubernetes, AWS IAM, OIDC, userpass, token, ...) |
| **AppRole** | An auth method for machine identity: `role_id` (username-like) + `secret_id` (password-like) → a client token |
| **Policy** | An HCL document listing paths and allowed capabilities; Vault denies by default |
| **Role** (in an auth method) | Binds an authenticated identity to one or more policies |
| **Token** | The universal Vault credential, carrying policies and a TTL, sent on every authenticated request |
| **Lease** | A tracked TTL on something Vault issued (a token, a dynamic secret), enabling auto/manual revocation |
| **TTL / Max TTL** | How long a token/lease is valid; max TTL caps how far renewals can extend it |
| **Namespace** | A Vault Enterprise feature for multi-tenant isolation within one cluster; not present in Community Edition |
| **Vault Agent** | A helper process that automates authentication, token renewal, and secret rendering to files |
| **Sidecar Injection** | A Kubernetes mutating webhook pattern that adds the Vault Agent container to a pod automatically |
| **Audit Device** | A Vault-side log of every request/response, for compliance and incident response |
| **Blast Radius** | How much is compromised if one particular credential leaks — the core reason for least privilege and short TTLs |
| **Least Privilege** | Granting only the exact access needed, nothing more — Vault's default-deny policy model enforces this by construction |

---

This concludes the documentation set. Return to [README.md](../README.md)
for the quick-start commands.
