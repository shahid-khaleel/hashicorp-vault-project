"""
app.py
------
A deliberately small Flask app whose only job is to prove out one idea:

    The application never stores a single secret. It only knows HOW to
    ask Vault for secrets, and what to do if Vault says no.

Three pages:
  /            Home       - is Vault reachable? Are we authenticated?
  /secrets     Secrets    - example secrets fetched live from Vault
  /vault-info  Vault Info - connection/auth metadata, for learning
"""

import logging

from flask import Flask, render_template

from config import Config
from vault_client import (
    VaultClient,
    VaultConnectionError,
    VaultAuthenticationError,
    VaultSecretNotFoundError,
    VaultPermissionDeniedError,
)

logging.basicConfig(level=logging.INFO)

app = Flask(__name__)
app.config.from_object(Config)

# One VaultClient per process. authenticate() is retried lazily on each
# request (see vault_client._ensure_authenticated) so a Vault restart or
# an expired token doesn't require restarting the Flask app.
vault = VaultClient(Config)


@app.route("/")
def home():
    context = {
        "app_name": Config.APP_NAME,
        "app_env": Config.APP_ENV,
        "vault_addr": Config.VAULT_ADDR,
        "vault_reachable": False,
        "vault_sealed": None,
        "authenticated": False,
        "error": None,
    }
    try:
        seal_status = vault.get_seal_status()
        context["vault_reachable"] = True
        context["vault_sealed"] = seal_status.get("sealed")

        vault.authenticate()
        context["authenticated"] = True
    except VaultConnectionError as exc:
        context["error"] = f"Cannot reach Vault: {exc}"
    except VaultAuthenticationError as exc:
        context["error"] = f"Vault authentication failed: {exc}"

    return render_template("home.html", **context)


@app.route("/secrets")
def secrets():
    context = {
        "app_name": Config.APP_NAME,
        "database": None,
        "api": None,
        "app_config": None,
        "error": None,
    }
    try:
        context["database"] = vault.read_secret("database")
        context["api"] = vault.read_secret("api")
        context["app_config"] = vault.read_secret("config")
    except VaultConnectionError as exc:
        context["error"] = ("connection", str(exc))
    except VaultAuthenticationError as exc:
        context["error"] = ("auth", str(exc))
    except VaultPermissionDeniedError as exc:
        context["error"] = ("permission", str(exc))
    except VaultSecretNotFoundError as exc:
        context["error"] = ("not_found", str(exc))

    return render_template("secrets.html", **context)


@app.route("/vault-info")
def vault_info():
    context = {
        "vault_addr": Config.VAULT_ADDR,
        "auth_method": Config.VAULT_AUTH_METHOD,
        "mount_point": Config.VAULT_MOUNT_POINT,
        "secret_path": Config.VAULT_SECRET_PATH,
        "namespace": Config.VAULT_NAMESPACE or "N/A (Community Edition has no namespaces)",
        "token_info": None,
        "error": None,
    }
    try:
        context["token_info"] = vault.get_token_info()
    except (VaultConnectionError, VaultAuthenticationError, VaultPermissionDeniedError) as exc:
        context["error"] = str(exc)

    return render_template("vault_info.html", **context)


@app.route("/health")
def health():
    """Plain-text style health check, the kind a container orchestrator
    or CI pipeline step would poll before routing traffic to this app."""
    try:
        vault.get_seal_status()
        vault.authenticate()
        return {"status": "ok", "vault_reachable": True, "vault_authenticated": True}
    except (VaultConnectionError, VaultAuthenticationError) as exc:
        return {"status": "degraded", "error": str(exc)}, 503


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=(Config.APP_ENV == "development"))
