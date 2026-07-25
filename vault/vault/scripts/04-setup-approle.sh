#!/bin/sh
# 04-setup-approle.sh
#
# WHAT: Enables the AppRole auth method and creates a role that is bound
#       to the "vault-demo-app" policy, then generates a role_id and
#       secret_id pair.
# WHY:  AppRole is how a MACHINE (a service, a CI job, a container - no
#       human at a keyboard) proves its identity to Vault, analogous to a
#       username+password but designed to be handed out programmatically:
#         - role_id  : like a username. Not secret. Often baked into a
#                      config file or CI variable.
#         - secret_id: like a password. IS secret. Should be short-lived,
#                      generated per-instance, and delivered by a secure
#                      out-of-band channel (e.g. a CI secret, a Vault
#                      Agent init container, cloud metadata + Vault's
#                      identity-based auto-auth) - never committed to git.
#       Trading a valid (role_id, secret_id) pair for a short-lived client
#       token is exactly what vault_client.py's _authenticate_approle()
#       does in this app.
set -e

vault auth enable approle \
  && echo "Enabled approle auth method" \
  || echo "approle auth method already enabled - continuing"

vault write auth/approle/role/vault-demo-app \
  token_policies="vault-demo-app" \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=24h \
  secret_id_num_uses=0

echo "AppRole 'vault-demo-app' created (token_ttl=1h, secret_id_ttl=24h)"

CREDS_DIR="${CREDS_DIR:-/vault-creds}"
mkdir -p "$CREDS_DIR"

vault read -field=role_id auth/approle/role/vault-demo-app/role-id > "$CREDS_DIR/role_id"
vault write -field=secret_id -f auth/approle/role/vault-demo-app/secret-id > "$CREDS_DIR/secret_id"

echo "role_id and secret_id written to $CREDS_DIR/"
echo "   (in a real pipeline these would go to a CI secret store, not a shared file)"
