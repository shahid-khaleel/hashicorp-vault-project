#!/bin/sh
# rotate-secret-demo.sh
#
# LEARNING SCENARIO: "Rotate the database password without touching
# application code or redeploying the app."
#
# Run this while the Flask app is running, then refresh the /secrets page
# in your browser - the password shown changes immediately. This is the
# whole point of centralized secret management: the app always asks Vault
# for the CURRENT value instead of having a value baked in at deploy time.
set -e

NEW_PASSWORD="Rotated-$(date +%s)!"

echo "Old value:"
vault kv get -field=password secret/vault-demo/database

vault kv put secret/vault-demo/database \
  username="app_db_user" \
  password="$NEW_PASSWORD"

echo "New value written: $NEW_PASSWORD"
echo ""
echo "KV v2 keeps history of every version:"
vault kv metadata get secret/vault-demo/database

echo ""
echo "Now reload http://localhost:5000/secrets - no app restart needed."
