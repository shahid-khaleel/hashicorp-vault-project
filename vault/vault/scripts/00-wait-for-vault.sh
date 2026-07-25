#!/bin/sh
# 00-wait-for-vault.sh
#
# Vault takes a moment to start accepting connections. Rather than racing
# it, we poll `vault status` until it responds. This is the same pattern
# CI pipelines and Kubernetes initContainers use before talking to any
# dependency (database, message queue, Vault, ...).
set -e

echo "Waiting for Vault at ${VAULT_ADDR:-http://127.0.0.1:8200} ..."
until vault status >/dev/null 2>&1; do
  sleep 1
done
echo "Vault is up."
