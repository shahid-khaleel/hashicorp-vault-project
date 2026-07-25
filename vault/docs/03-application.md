# 3. Application Documentation

[← Back to README](../README.md)

## UI Walkthrough

### Home (`/`)
Four status cards:
- **Vault Status** — "Reachable/Unreachable" (from `vault.sys.read_seal_status()`,
  called *without* authentication first — so this works even if login is
  broken, and tells you *which* half of the stack is failing) plus
  "Sealed/Unsealed".
- **Authentication Status** — whether `vault.authenticate()` succeeded
  with the configured `VAULT_AUTH_METHOD`.
- **Current Environment** — `APP_ENV` (`development` by default).
- **Connection Target** — the `VAULT_ADDR` this instance is configured
  with.

If anything failed, a red banner explains which error type occurred
(connection vs. authentication) in plain language.

### Secrets (`/secrets`)
Three cards, each explicitly labeled "from Vault":
- **Database Credentials** — username (plain) + password (masked, with a
  client-side Show/Hide toggle powered by `static/js/secrets.js`).
- **API Credentials** — API key (plain) + API secret (masked).
- **Application Configuration** — environment, region, feature flag.

Every value is fetched fresh on every page load — there is no caching
layer, intentionally, so the [secret rotation demo](04-vault-integration.md#secret-rotation)
is visibly immediate.

### Vault Info (`/vault-info`)
A metadata table: Vault address, auth method, secrets engine + mount
point, secret path prefix, live token TTL and attached policies (from
`auth/token/lookup-self`), and namespace (always "N/A" here — namespaces
are a Vault Enterprise feature).

## Backend Workflow

```
Browser request
   → Flask routes it (app.py)
   → route handler calls a VaultClient method
   → VaultClient ensures it's authenticated, then calls hvac
   → hvac makes an HTTP call to the Vault server
   → response flows back up, converted from hvac's dict shape
     into whatever the route needs
   → route builds a template context dict (including any error info)
   → Jinja2 renders the matching template
   → Flask returns HTML
```

Nothing is queued, cached, or backgrounded — every request is
synchronous and stateless (aside from the module-level `VaultClient`
instance holding whatever token it currently has).

## API Endpoints

| Method | Path | Purpose | Auth required from caller | Response |
|---|---|---|---|---|
| GET | `/` | Home/status page | No | HTML |
| GET | `/secrets` | Secrets page | No | HTML |
| GET | `/vault-info` | Vault connection metadata page | No | HTML |
| GET | `/health` | Machine-readable health check | No | JSON, `200` or `503` |

"Auth required from caller" is about *this app's* HTTP endpoints (none —
it's a demo with no user login system). The app itself is of course
authenticated *to Vault*, which is the entire point.

`/health` response shape:
```json
// 200
{"status": "ok", "vault_reachable": true, "vault_authenticated": true}
// 503
{"status": "degraded", "error": "Could not reach Vault at http://vault:8200: ..."}
```

## Authentication Flow (App → Vault)

See [04-vault-integration.md](04-vault-integration.md#authentication-methods)
for the full explanation. Summary: `vault_client.VaultClient.authenticate()`
dispatches to `_authenticate_token()` or `_authenticate_approle()` based
on `Config.VAULT_AUTH_METHOD`. AppRole (the default) trades a
`(role_id, secret_id)` pair for a client token via
`client.auth.approle.login(...)`; `hvac` stores that token on the client
object automatically for every subsequent call.

## Configuration Files

| File | Purpose |
|---|---|
| `config.py` | Single source of truth for all *non-secret* settings, read from env vars (with `.env` support for local runs via `python-dotenv`) |
| `.env.example` | Template to copy to `.env` for local hybrid dev — never committed with real values (`.env` is in `.gitignore`) |
| `docker-compose.yml` | Sets real environment variables directly for the containerized `web` service — `.env` is not used there |
| `vault/policies/app-policy.hcl` | Not app config exactly, but defines what the app's Vault token is *allowed* to do |

`config.py`'s `Config` class is intentionally a flat namespace of class
attributes (no validation framework, no nested schema) — appropriate for
a project this size; a larger app would likely use `pydantic-settings` or
similar.

## Logging

The app uses Python's standard `logging` module
(`logging.basicConfig(level=logging.INFO)` in `app.py`). `vault_client.py`
logs at module scope (`logger = logging.getLogger(__name__)`) and emits:

- `INFO` on successful authentication (`"Authenticated to Vault via %s"`)
- `WARNING` when a re-authentication is triggered (token was missing/invalid)
- `ERROR` when a secret read fails due to a missing path or denied
  permission

Flask's own request logs (via Werkzeug) show every HTTP request/response
with status code. For a real deployment you'd typically switch to
structured (JSON) logging shipped to a central aggregator — see
[Deployment → Logging](06-deployment.md#logging).

## Error Handling

`vault_client.py` defines four exception types, each mapping to a
distinct real-world failure:

| Exception | Raised when | HTTP-equivalent cause |
|---|---|---|
| `VaultConnectionError` | Vault unreachable (network/DNS/refused) | connection refused/timeout |
| `VaultAuthenticationError` | Login rejected or returned no valid token | `400`/`403` on the auth endpoint |
| `VaultSecretNotFoundError` | Path doesn't exist | `404`-equivalent (`InvalidPath`) |
| `VaultPermissionDeniedError` | Token valid, but policy denies the path | `403` |

`app.py` catches these per-route and puts a *specific, honest* message in
the template context instead of leaking a stack trace or showing a
generic 500 — `secrets.html` even branches its displayed message on the
error "kind" (`connection` / `auth` / `permission` / `not_found`) so a
learner can see exactly which failure mode they triggered (this is what
[Learning Scenario 3](04-vault-integration.md#access-denied-scenario)
exercises deliberately).

Next: [04-vault-integration.md](04-vault-integration.md) — the deep dive
on Vault itself.
