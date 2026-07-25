#!/bin/sh
# setup-all.sh
#
# Runs the whole one-time Vault setup in order. This is what the
# `vault-init` container in docker-compose.yml runs automatically on
# `docker compose up`. You can also run each numbered script by hand
# (see README.md "Infrastructure Setup") to see exactly what each step
# does in isolation - that's the point of splitting them up.
set -e

SCRIPT_DIR="$(dirname "$0")"

"$SCRIPT_DIR/00-wait-for-vault.sh"
"$SCRIPT_DIR/01-enable-secrets-engine.sh"
"$SCRIPT_DIR/02-create-secrets.sh"
"$SCRIPT_DIR/03-create-policy.sh"
"$SCRIPT_DIR/04-setup-approle.sh"

echo ""
echo "=========================================="
echo " Vault setup complete."
echo "=========================================="
