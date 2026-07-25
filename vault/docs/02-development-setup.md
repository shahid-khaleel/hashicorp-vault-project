# 2. Development Environment Setup

[← Back to README](../README.md)

Two supported ways to run this project locally:

- **A) Full Docker (recommended)** — `docker compose up`, nothing installed
  on your host except Docker. This is what [Quick Start](../README.md#quick-start)
  covers and what's used throughout the rest of the docs.
- **B) Hybrid local dev** — Vault runs in Docker, but `app.py` runs
  directly on your host in a Python virtual environment. Useful when
  you're actively editing Python code and want instant reload without
  rebuilding an image.

This document focuses on (B), since (A) is already covered by Quick
Start.

## Prerequisites

| Tool | Why | Check |
|---|---|---|
| Docker + Docker Compose | Runs the Vault server (and can run everything) | `docker --version` |
| Python 3.10+ | Runs `app.py` directly | `python --version` |
| pip | Installs dependencies | `pip --version` |
| A terminal | bash/zsh, or PowerShell on Windows | — |
| (Optional) Vault CLI | Convenient for `vault kv get/put` outside Docker | `vault version` |

You do **not** need the Vault CLI installed on your host — every script
in this repo runs it *inside* the `vault` container via `docker exec`.

## 1. Start Vault (and provisioning) only

```bash
docker compose up -d vault vault-init
```

This starts the Vault server and runs the one-shot provisioning job
(KV engine, sample secrets, policy, AppRole) — but does **not** start the
`web` container, since you're about to run that part yourself.

Confirm it worked:
```bash
docker compose logs vault-init --tail 5
# should end with "Vault setup complete."
```

## 2. Create a virtual environment

macOS/Linux:
```bash
python3 -m venv .venv
source .venv/bin/activate
```

Windows (PowerShell):
```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

A venv keeps this project's dependencies isolated from other Python
projects/your system Python — normal Python hygiene, unrelated to Vault
itself.

## 3. Install dependencies

```bash
pip install -r requirements.txt
```

This installs Flask, `hvac`, `python-dotenv`, and `gunicorn`. Note:
`gunicorn` depends on the Unix-only `fcntl` module — it installs fine on
Windows but won't *run* there. That's fine: it's only used for
[production deployment](06-deployment.md#running-the-application) on
Linux, never for local dev.

## 4. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env`. For local hybrid dev, **token auth is the pragmatic
choice** — you already have Vault's dev-mode root token, and you don't
need to go generate AppRole credentials just to poke at the app:

```dotenv
VAULT_ADDR=http://localhost:8200
VAULT_AUTH_METHOD=token
VAULT_TOKEN=root
```

`config.py` loads `.env` automatically via `python-dotenv` (only for
local runs — `docker compose` sets real container environment variables
directly, so `.env` is ignored there).

If you'd rather exercise the same AppRole path the Docker setup uses,
pull the generated credentials out of the shared volume instead:
```bash
docker run --rm -v vault_vault-creds:/creds alpine cat /creds/role_id
docker run --rm -v vault_vault-creds:/creds alpine cat /creds/secret_id
```
and set `VAULT_AUTH_METHOD=approle`, `VAULT_ROLE_ID=...`,
`VAULT_SECRET_ID=...` in `.env` instead.

## 5. Run the application locally

```bash
python app.py
```

Flask's dev server starts on `http://localhost:5000` with debug/auto-reload
enabled (`APP_ENV=development` in `.env`/defaults triggers this — see
`app.py`'s `if __name__ == "__main__"` block).

> Don't run this at the same time as the Docker `web` container — both
> want host port 5000. Use `docker compose stop web` first if it's
> running, or just don't start it (`docker compose up -d vault vault-init`
> from step 1 already skips it).

## 6. Verify the setup

```bash
curl http://localhost:5000/health
# {"status": "ok", "vault_reachable": true, "vault_authenticated": true}
```

Then open a browser to `http://localhost:5000/` and click through Home →
Secrets → Vault Info. The Vault Info page's "Authentication Method"
field should read whatever you set in `.env` (`token` or `approle`),
confirming your local `.env` is actually being picked up.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `ModuleNotFoundError: No module named 'flask'` | venv not activated, or deps not installed | Re-run step 2/3, confirm `(.venv)` shows in your prompt |
| `Cannot reach Vault` on the Home page | `vault` container isn't running, or wrong `VAULT_ADDR` | `docker compose ps`; for a **host-side** Python process it must be `http://localhost:8200`, not `http://vault:8200` (that hostname only resolves inside the Docker network) |
| `Address already in use` on port 5000 | Docker's `web` container already bound to it | `docker compose stop web` |
| Address already in use on port 8200 | Another Vault (or the `production-mode-demo.sh` server) is running | `docker ps` and stop the conflicting container |
| `.env` changes don't seem to take effect | Env var already set in your shell session takes priority | `python-dotenv` never overrides an already-set variable; `echo $VAULT_ADDR` (bash) / `$env:VAULT_ADDR` (PowerShell) to check, then unset it |
| AppRole login fails with local `.env` credentials | `vault` container was restarted since you pulled `role_id`/`secret_id` (dev mode is in-memory) | Re-run the `docker run --rm -v vault_vault-creds:/creds ...` commands from step 4 to get fresh values |

Next: [03-application.md](03-application.md) walks through what the
application actually does, page by page and endpoint by endpoint.
