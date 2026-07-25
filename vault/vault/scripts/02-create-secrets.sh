#!/bin/sh
# 02-create-secrets.sh
#
# WHAT: Writes three example STATIC secrets under secret/vault-demo/.
# WHY:  These are the values our Flask app reads on the /secrets page.
#       They are deliberately fake/harmless - this is a learning project,
#       never put real credentials in a demo repo.
#
# `vault kv put` overwrites the whole secret with the given key=value
# pairs and creates a new VERSION (KV v2 keeps history - see
# `vault kv metadata get` / the "Secret rotation" learning scenario).
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
