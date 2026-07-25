"""
config.py
---------
Centralizes every setting the app needs to talk to Vault.

Why this file exists: in a real DevOps environment, an application should
never hardcode secrets or environment-specific values in source code. This
file only reads *non-secret* configuration (addresses, paths, method names)
from environment variables. The actual secrets (passwords, API keys) are
NEVER defined here - they live only in Vault and are fetched at runtime by
vault_client.py.

All values have safe local-dev defaults so the app "just works" with the
provided docker-compose.yml, but every value is overridable via environment
variables - which is exactly how you'd point this same code at a Dev, QA,
UAT, or Production Vault cluster without changing a single line of code.
"""

import os

from dotenv import load_dotenv

# Only affects local, non-Docker runs: docker-compose.yml sets real
# environment variables directly, so this is a no-op there. When you run
# `python app.py` on your host against the Vault container, this is what
# picks up your .env file. Never overrides a variable that's already set.
load_dotenv()


class Config:
    # --- Application metadata (not secret, just descriptive) ---
    APP_NAME = os.environ.get("APP_NAME", "Vault Demo App")
    APP_ENV = os.environ.get("APP_ENV", "development")

    # --- Vault connection ---
    # VAULT_ADDR points at the Vault server. Inside docker-compose the
    # service name "vault" resolves via Docker's internal DNS.
    VAULT_ADDR = os.environ.get("VAULT_ADDR", "http://vault:8200")

    # VAULT_NAMESPACE is a Vault *Enterprise* feature (multi-tenant Vault).
    # Community Edition (what this project uses) has no namespaces, so this
    # is normally empty - it's included so you can see how a real app would
    # support Enterprise without code changes.
    VAULT_NAMESPACE = os.environ.get("VAULT_NAMESPACE", "")

    # --- Authentication method ---
    # "approle" is the realistic choice for a machine/service (no human,
    # no browser, no MFA prompt available). "token" is simpler and useful
    # for local experimentation. See README.md "Authentication Methods".
    VAULT_AUTH_METHOD = os.environ.get("VAULT_AUTH_METHOD", "approle")

    # Used only when VAULT_AUTH_METHOD=token
    VAULT_TOKEN = os.environ.get("VAULT_TOKEN", "")

    # Used only when VAULT_AUTH_METHOD=approle
    # role_id is like a username (safe to store in config/CI variables).
    # secret_id is like a password (must be injected securely, short-lived,
    # and never committed to source control).
    VAULT_ROLE_ID = os.environ.get("VAULT_ROLE_ID", "")
    VAULT_SECRET_ID = os.environ.get("VAULT_SECRET_ID", "")

    # --- Secrets engine / path layout ---
    # KV version 2 secrets engine, mounted at "secret/" (Vault's default).
    VAULT_MOUNT_POINT = os.environ.get("VAULT_MOUNT_POINT", "secret")
    # The folder-like prefix under that mount where our demo secrets live.
    VAULT_SECRET_PATH = os.environ.get("VAULT_SECRET_PATH", "vault-demo")

    # Flask
    SECRET_KEY = os.environ.get("FLASK_SECRET_KEY", "dev-only-flask-session-key")
