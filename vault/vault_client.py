"""
vault_client.py
----------------
A thin wrapper around the `hvac` library (the official-ish Python client
for HashiCorp Vault). This is the ONLY file in the app that talks to Vault
directly - app.py just calls methods on VaultClient and renders the result.

Why wrap hvac instead of calling it straight from app.py?
This mirrors how real applications are built: a small "secrets adapter"
layer that handles authentication, retries, and error translation, so the
rest of the app doesn't need to know Vault-specific exception types.

Covers, on purpose, the things that break in real deployments:
  - Vault unreachable (network/DNS issue, Vault container still starting)
  - Vault sealed (can't serve secrets until unsealed)
  - Authentication failure (bad role_id/secret_id or expired token)
  - Missing secret (wrong path, or someone deleted it)
  - Permission denied (policy doesn't grant access to this path)
  - Expired/near-expired token (needs re-authentication)
"""

import logging

import hvac
import hvac.exceptions
import requests.exceptions

logger = logging.getLogger(__name__)


class VaultConnectionError(Exception):
    """Raised when Vault cannot be reached at all (network/DNS/refused)."""


class VaultAuthenticationError(Exception):
    """Raised when login to Vault fails (bad credentials, sealed, etc.)."""


class VaultSecretNotFoundError(Exception):
    """Raised when a secret path doesn't exist."""


class VaultPermissionDeniedError(Exception):
    """Raised when the current token's policy doesn't allow this operation."""


class VaultClient:
    def __init__(self, config):
        self.config = config
        # hvac.Client just builds an HTTP client - it does NOT contact
        # Vault yet, so this line alone can't fail due to Vault being down.
        self.client = hvac.Client(
            url=config.VAULT_ADDR,
            namespace=config.VAULT_NAMESPACE or None,
        )
        self._authenticated = False

    # ------------------------------------------------------------------
    # Authentication
    # ------------------------------------------------------------------
    def authenticate(self):
        """
        Logs in to Vault using whichever method config.VAULT_AUTH_METHOD
        selects, and stores the resulting client token on self.client.

        Real apps call this once at startup, then again automatically
        whenever a call fails with "permission denied" / token expired,
        which is why it's a separate method rather than being baked into
        __init__.
        """
        try:
            if self.config.VAULT_AUTH_METHOD == "token":
                self._authenticate_token()
            elif self.config.VAULT_AUTH_METHOD == "approle":
                self._authenticate_approle()
            else:
                raise VaultAuthenticationError(
                    f"Unknown VAULT_AUTH_METHOD: {self.config.VAULT_AUTH_METHOD!r}"
                )
        except (requests.exceptions.ConnectionError, requests.exceptions.Timeout) as exc:
            # Vault container not up yet, wrong VAULT_ADDR, network issue...
            raise VaultConnectionError(f"Could not reach Vault at {self.config.VAULT_ADDR}: {exc}") from exc

        self._authenticated = self.client.is_authenticated()
        if not self._authenticated:
            raise VaultAuthenticationError("Vault login did not return a valid token")
        logger.info("Authenticated to Vault via %s", self.config.VAULT_AUTH_METHOD)
        return self._authenticated

    def _authenticate_token(self):
        if not self.config.VAULT_TOKEN:
            raise VaultAuthenticationError("VAULT_AUTH_METHOD=token but VAULT_TOKEN is empty")
        self.client.token = self.config.VAULT_TOKEN

    def _authenticate_approle(self):
        # AppRole is the standard way a *machine* (not a human) proves its
        # identity to Vault: role_id (who) + secret_id (proof), traded for
        # a short-lived client token. See README "AppRole Authentication".
        if not self.config.VAULT_ROLE_ID or not self.config.VAULT_SECRET_ID:
            raise VaultAuthenticationError(
                "VAULT_AUTH_METHOD=approle but VAULT_ROLE_ID/VAULT_SECRET_ID are not set"
            )
        try:
            self.client.auth.approle.login(
                role_id=self.config.VAULT_ROLE_ID,
                secret_id=self.config.VAULT_SECRET_ID,
            )
        except hvac.exceptions.InvalidRequest as exc:
            raise VaultAuthenticationError(f"AppRole login rejected: {exc}") from exc

    def _ensure_authenticated(self):
        """Re-authenticate on demand if we don't yet have a live token."""
        try:
            if not self.client.is_authenticated():
                logger.warning("No valid Vault token - re-authenticating")
                self.authenticate()
        except (requests.exceptions.ConnectionError, requests.exceptions.Timeout) as exc:
            raise VaultConnectionError(f"Could not reach Vault at {self.config.VAULT_ADDR}: {exc}") from exc

    # ------------------------------------------------------------------
    # Status / introspection (used by the Home and Vault Info pages)
    # ------------------------------------------------------------------
    def get_seal_status(self):
        """Unauthenticated call - works even before login. Tells us if
        Vault is initialized/sealed, which explains WHY reads might fail."""
        try:
            return self.client.sys.read_seal_status()
        except (requests.exceptions.ConnectionError, requests.exceptions.Timeout) as exc:
            raise VaultConnectionError(f"Could not reach Vault at {self.config.VAULT_ADDR}: {exc}") from exc

    def get_token_info(self):
        """Returns TTL, policies, and other metadata for the CURRENT token.
        This is what powers the 'Token TTL' field on the Vault Info page -
        it's what `vault token lookup` does under the hood."""
        self._ensure_authenticated()
        try:
            return self.client.auth.token.lookup_self()["data"]
        except hvac.exceptions.Forbidden as exc:
            raise VaultPermissionDeniedError(str(exc)) from exc

    # ------------------------------------------------------------------
    # Secrets
    # ------------------------------------------------------------------
    def read_secret(self, relative_path):
        """
        Reads one KV-v2 secret, e.g. relative_path="database" reads
        secret/data/vault-demo/database and returns just the data dict
        (e.g. {"username": ..., "password": ...}).
        """
        self._ensure_authenticated()
        full_path = f"{self.config.VAULT_SECRET_PATH}/{relative_path}"
        try:
            response = self.client.secrets.kv.v2.read_secret_version(
                path=full_path,
                mount_point=self.config.VAULT_MOUNT_POINT,
                raise_on_deleted_version=True,
            )
            return response["data"]["data"]
        except hvac.exceptions.InvalidPath as exc:
            logger.error("Secret not found at %s/%s", self.config.VAULT_MOUNT_POINT, full_path)
            raise VaultSecretNotFoundError(
                f"No secret found at {self.config.VAULT_MOUNT_POINT}/{full_path}"
            ) from exc
        except hvac.exceptions.Forbidden as exc:
            logger.error("Permission denied reading %s", full_path)
            raise VaultPermissionDeniedError(
                f"Token's policy does not allow reading {full_path}: {exc}"
            ) from exc
        except (requests.exceptions.ConnectionError, requests.exceptions.Timeout) as exc:
            raise VaultConnectionError(f"Could not reach Vault at {self.config.VAULT_ADDR}: {exc}") from exc
