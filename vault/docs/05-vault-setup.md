# 5. Vault Setup Documentation

[← Back to README](../README.md)

Command-by-command walkthrough of provisioning Vault, both the way this
project automates it (Docker, dev mode) and the way you'd do it against a
real, persistent server.

## Installing HashiCorp Vault

**This project (Docker — no local install needed):**
```yaml
image: hashicorp/vault:1.17
```
Nothing to install on your host; `docker compose up` pulls the image.

**Installing the Vault binary natively** (useful for running the CLI
against a remote Vault, or for a genuinely production host):

```bash
# macOS (Homebrew)
brew tap hashicorp/tap
brew install hashicorp/tap/vault

# Ubuntu/Debian
wget -O- https://apt.releases.hashicorp.com/gpg | sudo gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/hashicorp.list
sudo apt update && sudo apt install vault

# Windows (Chocolatey)
choco install vault
```
```bash
vault version
```

## Initializing Vault

**Skipped by dev mode** (this project's default) — dev mode
auto-initializes with one unseal key and prints a root token.

**On a real (non-dev) server**, initialization is a one-time event per
storage backend:
```bash
vault operator init
```
Output (abbreviated):
```
Unseal Key 1: abc123...
Unseal Key 2: def456...
Unseal Key 3: ghi789...
Unseal Key 4: jkl012...
Unseal Key 5: mno345...

Initial Root Token: hvs.xxxxxxxx
```
This creates Vault's master encryption key, splits it into 5 shares
(Shamir's Secret Sharing; any 3 reconstruct it), and prints the initial
root token. **This output is shown exactly once** — store it somewhere
durable (in a real org: split across key custodians, or better, replace
this whole flow with cloud KMS auto-unseal so no human ever holds a raw
key share).

Run this yourself against a throwaway server:
```bash
sh vault/scripts/production-mode-demo.sh
```

## Unsealing Vault

Required every time the Vault **process** restarts (not per-request).
Supply the threshold number of *different* unseal keys:
```bash
vault operator unseal   # run 3 times, a different key each time
```
After the third valid key, Vault reports `"sealed": false` and starts
serving requests normally.

## Creating Policies

```bash
vault policy write vault-demo-app vault/policies/app-policy.hcl
vault policy list
vault policy read vault-demo-app
```
See [04-vault-integration.md → Vault Policies](04-vault-integration.md#vault-policies-and-access-control)
for what the HCL actually means.

## Enabling Authentication Methods

```bash
vault auth enable approle          # for machines/services (used by this app)
vault auth list                    # confirm it's enabled, see the mount path
```
For human operators you'd typically also enable one of:
```bash
vault auth enable userpass         # simple username/password, fine for small teams/demos
vault auth enable oidc             # SSO via your identity provider — the real answer for orgs
```

## Creating Users/Tokens

**Machine identity (what this project uses) — AppRole:**
```bash
vault write auth/approle/role/vault-demo-app \
  token_policies="vault-demo-app" token_ttl=1h token_max_ttl=4h secret_id_ttl=24h

vault read -field=role_id auth/approle/role/vault-demo-app/role-id
vault write -field=secret_id -f auth/approle/role/vault-demo-app/secret-id
```

**Human identity — userpass example (not used by this project, shown for completeness):**
```bash
vault auth enable userpass
vault write auth/userpass/users/alice password="change-me" policies="vault-demo-app"
vault login -method=userpass username=alice
```

**A plain token** (what dev mode gives you as `root`, and what
`VAULT_AUTH_METHOD=token` uses locally):
```bash
vault token create -policy=vault-demo-app -ttl=1h
```

## Configuring the KV Secrets Engine

```bash
vault secrets enable -path=secret -version=2 kv
vault secrets list
```
(Dev mode does this automatically — the script runs it anyway so it's
explicit and portable to a non-dev server.)

## Storing Secrets

```bash
vault kv put secret/vault-demo/database username="app_db_user" password="SuperS3cretDbPass!"
vault kv put secret/vault-demo/api api_key="demo-api-key-12345" api_secret="demo-api-secret-67890"
vault kv put secret/vault-demo/config environment="development" region="us-east-1" feature_flag="true"
```

## Retrieving Secrets

```bash
vault kv get secret/vault-demo/database
vault kv get -field=password secret/vault-demo/database   # just one field
vault kv get -format=json secret/vault-demo/database       # for scripting
```

## Testing the Configuration

```bash
vault status                                  # sealed? initialized? HA?
vault token lookup                            # who am I, what can I do, how long left
vault write auth/approle/login role_id=... secret_id=...   # simulate the app's login
curl http://127.0.0.1:5000/health              # confirm the app agrees Vault is reachable+authenticated
```

## Backup and Recovery

Vault's own data is only as durable as its storage backend:

- **Dev mode (this project's default)** — in-memory. There is nothing to
  back up; a restart is a full data loss. Never use dev mode for
  anything you'd mind losing.
- **File storage backend** (`vault/production-demo/config.hcl`) — back up
  the `/vault/file` directory. It's encrypted at rest, so the backup
  alone is useless without also safely retaining the unseal keys.
- **Integrated Storage (Raft)** — Vault has a built-in snapshot command:
  ```bash
  vault operator raft snapshot save backup.snap
  vault operator raft snapshot restore backup.snap
  ```
- Regardless of backend: **losing your unseal keys/recovery keys makes
  even a perfectly intact backup permanently undecryptable.** Key
  custody is part of the backup plan, not an afterthought.

## Security Hardening

- **TLS everywhere** — `tls_disable = "true"` in this repo's
  `production-demo/config.hcl` is only acceptable for a localhost
  throwaway container; a real listener block always terminates TLS.
- **Auto-unseal via cloud KMS** — removes the "5 humans each holding a
  key share" operational burden and the risk of losing quorum.
- **Enable an audit device** — `vault audit enable file file_path=/vault/logs/audit.log`
  (or a syslog/socket device) so every request is logged for compliance.
- **Least-privilege policies per app/environment** — never one shared
  "god" policy across multiple services.
- **Short TTLs, prefer dynamic secrets** — see
  [04-vault-integration.md → Security Best Practices](04-vault-integration.md#security-best-practices).
- **Network-restrict the Vault API** — it should not be reachable from
  the public internet; put it behind a private network/VPN/service mesh.
- **Revoke the initial root token after setup** — it's meant for
  bootstrapping only; day-to-day operations should use scoped tokens tied
  to real auth methods and policies.

Next: [06-deployment.md](06-deployment.md) covers taking the *application*
(not just Vault) to a real server.
