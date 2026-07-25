#!/bin/sh
# Writes three example STATIC secrets under secret/vault-demo/ - the
# values VaultService.readSecret() fetches for the Secrets page. Fake and
# harmless on purpose; never do this with real credentials.
set -e

vault kv put secret/vault-demo/database \
  username="app_db_user" \
  password="SuperS3cretDbPass!"

vault kv put secret/vault-demo/api \
  api_key="demo-api-key-12345" \
  api_secret="demo-api-secret-67890"

vault kv put secret/vault-demo/config \
  environment="development" \
  region="us-east-1" \
  feature_flag="true"

echo "Sample secrets written under secret/vault-demo/"
