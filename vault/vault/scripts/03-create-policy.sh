#!/bin/sh
# 03-create-policy.sh
#
# WHAT: Loads app-policy.hcl into Vault under the name "vault-demo-app".
# WHY:  A policy is Vault's authorization layer - it lists exactly which
#       paths a token may act on, and with which capabilities (read,
#       create, update, delete, list, sneak-in "deny"). Authentication
#       (proving who you are) and Authorization (what you're allowed to
#       do) are separate steps in Vault, same as everywhere else in
#       security. This script defines the WHAT-you-can-do half; step 04
#       (AppRole) defines the WHO-are-you half and binds them together.
set -e

POLICY_FILE="${POLICY_FILE:-/vault-config/policies/app-policy.hcl}"

vault policy write vault-demo-app "$POLICY_FILE"
echo "Policy 'vault-demo-app' created from $POLICY_FILE"
