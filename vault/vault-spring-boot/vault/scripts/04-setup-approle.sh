#!/bin/sh
# Enables AppRole and creates a role bound to the vault-demo-app policy,
# then writes the generated role_id/secret_id to a shared volume that
# docker-entrypoint.sh reads on container start. Same mechanism as the
# sibling Python project - the app itself (VaultService.java) is what
# actually trades these for a client token via AppRoleAuthentication.
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
