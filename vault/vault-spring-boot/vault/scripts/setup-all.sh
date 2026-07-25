#!/bin/sh
# Runs the one-time Vault setup in order. This is what the vault-init
# container runs automatically on `docker compose up`; run the numbered
# scripts individually by hand to see each step in isolation.
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
