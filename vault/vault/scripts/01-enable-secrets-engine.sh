#!/bin/sh
# 01-enable-secrets-engine.sh
#
# WHAT: Enables the KV (Key-Value) secrets engine, version 2, mounted at
#       the path "secret/".
# WHY:  Vault doesn't store anything until you "mount" a secrets engine.
#       Think of a secrets engine like a plugin - KV is the simplest one
#       (just stores what you give it), but Vault also ships engines that
#       generate DYNAMIC secrets on demand (database credentials, AWS IAM
#       keys, PKI certificates) that don't exist until requested and
#       expire automatically. See README.md "Dynamic vs Static Secrets".
#
# NOTE: `vault server -dev` (used by this project) auto-mounts a KV v2
# engine at secret/ already. We run this anyway so the command is explicit
# and so this script also works against a non-dev Vault server.
set -e

vault secrets enable -path=secret -version=2 kv \
  && echo "Enabled kv-v2 at secret/" \
  || echo "secret/ already enabled - continuing"
