#!/bin/sh
# LEARNING SCENARIO: rotate the database password while the app is
# running, then reload /secrets in the browser - the new value appears
# immediately, with zero application code changes and zero restart.
set -e

NEW_PASSWORD="Rotated-$(date +%s)!"

echo "Old value:"
vault kv get -field=password secret/vault-demo/database

vault kv put secret/vault-demo/database \
  username="app_db_user" \
  password="$NEW_PASSWORD"

echo "New value written: $NEW_PASSWORD"
echo ""
vault kv metadata get secret/vault-demo/database

echo ""
echo "Now reload http://localhost:8080/secrets - no app restart needed."
