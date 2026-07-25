# Vault Demo App (Spring Boot) — A DevOps Learning Project

The same learning project as the sibling
[`vault` (Python/Flask)](../README.md) project, rebuilt in
**Java + Spring Boot + Spring Vault** — same three pages, same secret
paths, same Vault policy/AppRole pattern, same learning scenarios. The
point of having both: the *application* language is incidental; the
*Vault workflow* (authenticate, read, handle failure, rotate) is
identical regardless of which client library or framework wraps it.

If you haven't already, the Python project's `docs/04-vault-integration.md`
is the deepest single explanation of the Vault concepts themselves — this
README focuses on what's specific to the Spring Boot implementation.

## Quick Start

Requirements: Docker + Docker Compose.

```bash
docker compose up -d --build
```

Three containers, same shape as the Python project, different ports so
both stacks can run side by side:

| Service      | What it is                                                          | Port |
|--------------|----------------------------------------------------------------------|------|
| `vault`      | Vault server, dev mode (root token = `root`)                        | `8300` (host) → `8200` |
| `vault-init` | One-shot provisioning job (KV engine, secrets, policy, AppRole)      | — |
| `web`        | The Spring Boot app                                                   | `8080` |

Open:
- **http://localhost:8080/** — Home
- **http://localhost:8080/secrets** — Secrets fetched live from Vault
- **http://localhost:8080/vault-info** — Connection & token metadata
- **http://localhost:8300/ui** — Vault's own UI (token `root`)

Rotation demo:
```bash
docker exec -e VAULT_TOKEN=root vault-demo-server-java sh /vault-config/scripts/rotate-secret-demo.sh
```
Reload `/secrets` — new password, zero app restart.

Shut down: `docker compose down` (`-v` also wipes the AppRole credentials volume).

## Why Spring Vault Directly, Not Spring Cloud Vault

Spring Cloud Vault can inject secrets straight into `@Value`-annotated
fields as if they were regular `application.properties`, resolved
automatically at startup via a `PropertySource`. That's convenient in
production, but it hides exactly the mechanics this project exists to
teach: *when* does authentication happen, *what* does a failed read look
like, *how* do you recover from an expired token?

Instead, this project uses **`spring-vault-core`** directly — the same
tier of library as Python's `hvac` — and wraps it in one class,
`VaultService`, that every controller calls explicitly. Every Vault
interaction is a traceable method call, not a startup-time property
resolution you can't easily observe. `vault_client.py` in the Python
project follows the exact same philosophy.

## Project Structure

```
vault-spring-boot/
├── pom.xml
├── Dockerfile                     # Multi-stage: Maven build -> slim JRE runtime
├── docker-compose.yml             # vault + vault-init + web
├── docker-entrypoint.sh           # Loads AppRole creds from a file before starting the JVM
├── src/main/java/com/example/vaultdemo/
│   ├── VaultDemoApplication.java
│   ├── config/AppProperties.java  # application.yml-bound config (Java's config.py equivalent)
│   ├── service/VaultService.java  # THE only class that imports spring-vault-core
│   ├── controller/PageController.java   # / , /secrets , /vault-info
│   ├── controller/ApiController.java    # /health
│   └── exception/                 # VaultConnectionException, VaultAuthenticationException,
│                                   # VaultSecretNotFoundException, VaultPermissionDeniedException
├── src/main/resources/
│   ├── application.yml            # Default config, overridable by env vars
│   ├── templates/                 # Thymeleaf HTML (fragments.html + home/secrets/vault-info)
│   └── static/                    # CSS + the same Show/Hide JS as the Python project
└── vault/
    ├── policies/app-policy.hcl    # Identical policy to the Python project
    └── scripts/                   # Identical provisioning scripts (generic Vault CLI, not Java-specific)
```

## Configuration

Every setting is a Spring Boot `@ConfigurationProperties` field under the
`app` prefix (`AppProperties.java`), overridable by environment variable
via Spring's relaxed binding (`app.vault.role-id` ⇄ `APP_VAULT_ROLE_ID`):

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `app.name` | `APP_NAME` | `Vault Demo App (Spring Boot)` | Display name |
| `app.env` | `APP_ENV` | `development` | Shown on the Home page |
| `app.vault.address` | `APP_VAULT_ADDRESS` | `http://vault:8200` | Vault server URL |
| `app.vault.auth-method` | `APP_VAULT_AUTH_METHOD` | `approle` | `approle` or `token` |
| `app.vault.token` | `APP_VAULT_TOKEN` | *(empty)* | Used only for `token` auth |
| `app.vault.role-id` / `app.vault.secret-id` | `APP_VAULT_ROLE_ID` / `APP_VAULT_SECRET_ID` | *(from shared volume)* | Used only for `approle` auth |
| `app.vault.mount-point` | `APP_VAULT_MOUNT_POINT` | `secret` | KV v2 mount |
| `app.vault.secret-path` | `APP_VAULT_SECRET_PATH` | `vault-demo` | Prefix under the mount |

## How Authentication Works (`VaultService.java`)

```java
AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
    .roleId(AppRoleAuthenticationOptions.RoleId.provided(roleId))
    .secretId(AppRoleAuthenticationOptions.SecretId.provided(secretId))
    .build();
ClientAuthentication auth = new AppRoleAuthentication(options, restTemplate);
VaultTemplate template = new VaultTemplate(endpoint, requestFactory, new SimpleSessionManager(auth));
```
`SimpleSessionManager` logs in **lazily** — constructing the `VaultTemplate`
makes no network call; the first operation that actually needs a token
(e.g. reading a secret) triggers the AppRole login. This mirrors
`hvac.Client(...)` in the Python project: cheap to build, authenticates
only on first real use.

Because `SimpleSessionManager` caches its token forever (no automatic
renewal), `VaultService.withAuthRetry(...)` wraps every operation: on an
HTTP 401, it calls `reset()` (forces a fresh `VaultTemplate`/login on the
next call) and retries once. This is the same recovery strategy as the
Python project's `_ensure_authenticated()` — different mechanism
(Spring's exception-driven retry vs. Python's `is_authenticated()`
pre-check), same goal: survive a token expiring or Vault restarting
without requiring an app restart.

## Reading Secrets

```java
VaultKeyValueOperations kv = template.opsForKeyValue("secret", KeyValueBackend.KV_2);
VaultResponse response = kv.get("vault-demo/database");
Map<String, Object> data = response.getData();
```
Note there's no manual `secret/data/...` path construction here —
Spring Vault's `VaultKeyValueOperations` handles the KV v2 `data/`
prefix internally (unlike raw `hvac`/raw HTTP calls, where you build that
path yourself — see the Python project's
[docs/04-vault-integration.md](../docs/04-vault-integration.md#how-secrets-are-retrieved)
for the manual version of the same request).

## Local (Non-Docker) Development

```bash
docker compose up -d vault vault-init   # Vault only, on host port 8300
```
Then, with a JDK 11+ installed (Maven itself isn't required - use the
bundled wrapper):
```bash
export APP_VAULT_ADDRESS=http://localhost:8300
export APP_VAULT_AUTH_METHOD=token
export APP_VAULT_TOKEN=root
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```
Same pragmatic choice as the Python project's local dev flow: token auth
against the dev-mode root token, no need to go generate AppRole
credentials just to iterate on the code. Open `http://localhost:8080/`.

## Troubleshooting

Same failure modes as the Python project (dev-mode Vault is in-memory,
so restarting the `vault` container invalidates any already-issued
AppRole `secret_id` held by a running `web` container — fix with
`docker compose restart web`). See the Python project's
[docs/08-appendices.md](../docs/08-appendices.md#common-errors-and-fixes)
for the full table; every entry there applies here too, just with Java
stack traces instead of Python ones.

One Java-specific gotcha hit while building this: naming a Thymeleaf
fragment `head` collides with the literal HTML `<head>` tag during
fragment-selector resolution (`fragments :: head(...)` matches the tag,
not the fragment) — this project's fragment is named `pagehead` instead
(see `templates/fragments.html`).

## Learning Scenarios

Identical to the Python project (same secret paths, same policy shape),
adjusted for this stack's container names/ports:

```bash
# Secret rotation, no restart
docker exec -e VAULT_TOKEN=root vault-demo-server-java sh /vault-config/scripts/rotate-secret-demo.sh

# Access denied: swap the AppRole's policy, then docker compose restart web
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=root vault-demo-server-java \
  vault policy write deny-all -<<'EOF'
path "secret/data/vault-demo/*" { capabilities = ["deny"] }
EOF
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=root vault-demo-server-java \
  vault write auth/approle/role/vault-demo-app token_policies="deny-all"
docker compose restart web
```
