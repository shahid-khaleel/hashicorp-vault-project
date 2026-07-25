#!/bin/sh
# Mounts the KV v2 secrets engine at secret/. Dev mode does this
# automatically already; run explicitly so it also works against a
# non-dev server, and so the step isn't hidden.
set -e

vault secrets enable -path=secret -version=2 kv \
  && echo "Enabled kv-v2 at secret/" \
  || echo "secret/ already enabled - continuing"
