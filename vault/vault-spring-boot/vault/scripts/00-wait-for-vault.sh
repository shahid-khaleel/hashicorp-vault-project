#!/bin/sh
# Polls until Vault answers, instead of a hardcoded sleep. Same pattern
# used by the sibling Python project and by real CI/K8s initContainers.
set -e

echo "Waiting for Vault at ${VAULT_ADDR:-http://127.0.0.1:8200} ..."
until vault status >/dev/null 2>&1; do
  sleep 1
done
echo "Vault is up."
