# Vault Demo App — A DevOps Learning Project

A deliberately small Flask app whose entire purpose is to teach **how
applications integrate with HashiCorp Vault** — from the perspective of a
DevOps/Platform engineer, not a software architect. There is no advanced
Python here on purpose. The interesting part is the *workflow*: how a
running app proves its identity to Vault and pulls secrets it never
stored itself.

By the end of the docs in this repo you should be able to explain, in an
interview or on the job: what Vault is, why it exists, how apps
authenticate to it, how secrets are stored/retrieved, how it fits into
CI/CD and Kubernetes, and how to troubleshoot it when something breaks.

## Quick Start

Requirements: Docker + Docker Compose.

```bash
docker compose up -d --build
```

This starts three containers, in order:

| Service      | What it is                                                                 |
|--------------|-----------------------------------------------------------------------------|
| `vault`      | Vault server, **dev mode** (auto-unsealed, root token = `root`)             |
| `vault-init` | One-shot job: provisions Vault (secrets, policy, AppRole), then exits(0)    |
| `web`        | The Flask app, port `5000`                                                   |

Open:
- **http://localhost:5000/** — Home (connection/auth status)
- **http://localhost:5000/secrets** — Secrets fetched live from Vault
- **http://localhost:5000/vault-info** — Connection & token metadata
- **http://localhost:8200/ui** — Vault's own UI (login with token `root`)

Try the rotation demo while the app is running:

```bash
docker exec -e VAULT_TOKEN=root vault-demo-server sh /vault-config/scripts/rotate-secret-demo.sh
```

Then refresh `/secrets` — the database password changes with **zero app
code changes and zero app restart**. That one demo is the entire point of
centralized secret management.

Shut everything down: `docker compose down` (add `-v` to also wipe the
generated AppRole credentials volume).

> **Note on dev mode:** the `vault` service runs `vault server -dev`,
> which auto-initializes and auto-unseals itself and hands you a root
> token for free. That's great for learning the *application* side
> quickly, but it **skips two of the most important Vault concepts**:
> initialization and unsealing. Run
> `sh vault/scripts/production-mode-demo.sh` to practice that workflow
> by hand against a separate, throwaway, realistic Vault server.

## Documentation

This README is just the entry point. The full documentation set lives in
[`docs/`](docs/):

| Doc | Covers |
|---|---|
| [01 — Project Overview](docs/01-project-overview.md) | Architecture diagrams, components, tech stack, folder structure, data flow |
| [02 — Development Setup](docs/02-development-setup.md) | Prerequisites, venv, local hybrid dev, env vars, verification, troubleshooting |
| [03 — Application](docs/03-application.md) | UI walkthrough, API endpoints, auth flow, config, logging, error handling |
| [04 — Vault Integration](docs/04-vault-integration.md) | **Primary focus.** Vault concepts end to end, plus the exact request/response flow |
| [05 — Vault Setup](docs/05-vault-setup.md) | Install, init, unseal, policies, auth methods, KV engine, backup/recovery, hardening |
| [06 — Deployment](docs/06-deployment.md) | Taking the app to a real server/cluster: config, WSGI server, rollback, monitoring |
| [07 — DevOps Perspective](docs/07-devops-perspective.md) | Who does what, CI/CD integration, Kubernetes + Vault Agent, scaling, disaster recovery |
| [08 — Appendices](docs/08-appendices.md) | FAQ, common errors, CLI cheat sheet, config reference, glossary |

## Project Structure

```
vault/
├── app.py                    # Flask routes
├── config.py                 # Env-var configuration (+ .env loading)
├── vault_client.py           # The ONLY file that talks to Vault (hvac wrapper)
├── docker-entrypoint.sh      # Loads AppRole creds from a file before starting Flask
├── requirements.txt / Dockerfile / docker-compose.yml
├── templates/ static/        # Jinja2 HTML + CSS/JS
├── vault/
│   ├── policies/app-policy.hcl
│   ├── scripts/               # Numbered, individually runnable Vault setup steps
│   └── production-demo/       # Config for the manual init/unseal demo
├── k8s-examples/              # Reference-only Kubernetes + Vault Agent manifest
└── docs/                      # Full documentation set (see table above)
```

See [01-project-overview.md](docs/01-project-overview.md) for what each
piece is responsible for and how they connect.

## Same Project, Different Stack

[`vault-spring-boot/`](vault-spring-boot/) is the same learning project
rebuilt in **Java + Spring Boot + Spring Vault** — same three pages, same
Vault policy/AppRole pattern, same learning scenarios, running side by
side on different ports (`8300`/`8080` vs. this project's `8200`/`5000`).
Its README explains what's specific to the Spring Vault implementation
and links back here for the Vault concepts themselves.

## Learning Scenarios

Short version — full details in
[04-vault-integration.md](docs/04-vault-integration.md) and
[07-devops-perspective.md](docs/07-devops-perspective.md):

- **Secret rotation without a restart** — `rotate-secret-demo.sh`
- **Token expiration** — issue a short-TTL token, watch re-auth fail/succeed
- **Access denied** — swap the AppRole's policy to `deny-all` and watch the app surface a clean permission error
- **Init/unseal from scratch** — `production-mode-demo.sh`
