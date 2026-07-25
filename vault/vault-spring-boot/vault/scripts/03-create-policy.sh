#!/bin/sh
# Loads app-policy.hcl into Vault under the name "vault-demo-app" -
# the authorization half; 04-setup-approle.sh does the authentication half.
set -e

POLICY_FILE="${POLICY_FILE:-/vault-config/policies/app-policy.hcl}"

vault policy write vault-demo-app "$POLICY_FILE"
echo "Policy 'vault-demo-app' created from $POLICY_FILE"
