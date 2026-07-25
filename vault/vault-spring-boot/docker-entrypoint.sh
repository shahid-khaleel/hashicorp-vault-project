#!/bin/sh
# docker-entrypoint.sh
#
# Same hand-off pattern as the sibling Python project: the vault-init
# container writes AppRole role_id/secret_id to files on a shared volume
# (it can't be a fixed compose environment variable - those credentials
# don't exist until vault-init runs). This script reads that file once
# and exports it as Spring Boot environment-variable configuration
# (APP_VAULT_ROLE_ID -> app.vault.role-id, via Spring's relaxed binding)
# before starting the JVM.
set -e

CREDS_DIR="${VAULT_CREDS_DIR:-/vault-creds}"

if [ -f "$CREDS_DIR/role_id" ] && [ -f "$CREDS_DIR/secret_id" ]; then
  export APP_VAULT_ROLE_ID
  export APP_VAULT_SECRET_ID
  APP_VAULT_ROLE_ID="$(cat "$CREDS_DIR/role_id")"
  APP_VAULT_SECRET_ID="$(cat "$CREDS_DIR/secret_id")"
  echo "Loaded AppRole credentials from $CREDS_DIR"
else
  echo "No AppRole credential files found at $CREDS_DIR - falling back to whatever APP_VAULT_* env vars are already set"
fi

exec "$@"
