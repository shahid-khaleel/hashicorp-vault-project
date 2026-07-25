#!/bin/sh
# docker-entrypoint.sh
#
# The `vault-init` container (see docker-compose.yml) authenticates to
# Vault via AppRole and writes the resulting role_id/secret_id to files on
# a volume shared with this container - it does NOT put them in an
# environment variable, because compose env vars are fixed at "up" time,
# before those credentials exist.
#
# This mirrors a real pattern: a Vault Agent or an init container renders
# credentials to a file, and the application container reads that file at
# startup. Here we just read the file once and export it as an env var
# before starting Flask, since app.py/config.py already expect env vars.
set -e

CREDS_DIR="${VAULT_CREDS_DIR:-/vault-creds}"

if [ -f "$CREDS_DIR/role_id" ] && [ -f "$CREDS_DIR/secret_id" ]; then
  export VAULT_ROLE_ID
  export VAULT_SECRET_ID
  VAULT_ROLE_ID="$(cat "$CREDS_DIR/role_id")"
  VAULT_SECRET_ID="$(cat "$CREDS_DIR/secret_id")"
  echo "Loaded AppRole credentials from $CREDS_DIR"
else
  echo "No AppRole credential files found at $CREDS_DIR - falling back to whatever VAULT_* env vars are already set"
fi

exec "$@"
