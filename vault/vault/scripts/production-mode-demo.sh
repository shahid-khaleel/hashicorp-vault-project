#!/bin/sh
# production-mode-demo.sh
#
# The main docker-compose.yml runs Vault in DEV MODE (`vault server -dev`)
# because it auto-unseals and auto-creates a root token, which keeps the
# main learning path (running the Flask app) friction-free. But dev mode
# SKIPS two of the most important, most-asked-about-in-interviews Vault
# concepts: initialization and unsealing.
#
# This script spins up a SEPARATE, throwaway, non-dev Vault server (file
# storage backend, no auto-unseal) purely so you can practice that
# workflow by hand. It does not touch the app or the main compose stack.
#
# Run this from the project root: sh vault/scripts/production-mode-demo.sh
set -e

echo "== Starting a non-dev Vault server on http://localhost:8210 =="
docker run -d --name vault-prod-demo \
  -p 8210:8200 \
  -v "$(pwd)/vault/production-demo/config.hcl:/vault/config/config.hcl:ro" \
  -v vault-prod-demo-data:/vault/file \
  --cap-add=IPC_LOCK \
  hashicorp/vault:1.17 server -config=/vault/config/config.hcl

echo "Waiting a few seconds for the server process to start..."
sleep 3

cat <<'EOF'

Vault is running but NOT YET USABLE. Two things must happen first:

1) INITIALIZE (one-time, ever, for this storage backend)
   Creates Vault's master encryption key and splits it into key shares
   (Shamir's Secret Sharing: by default 5 shares, any 3 reconstruct the
   key). Also prints the Initial Root Token. Save this output - it is
   shown exactly once:

     docker exec vault-prod-demo vault operator init

2) UNSEAL (required every time the Vault process restarts)
   A sealed Vault has the encrypted data on disk but no key in memory to
   decrypt it - it will refuse every request except unseal/status. Supply
   3 DIFFERENT unseal keys from step 1, one per command:

     docker exec -it vault-prod-demo vault operator unseal
     docker exec -it vault-prod-demo vault operator unseal
     docker exec -it vault-prod-demo vault operator unseal

3) LOG IN with the root token from step 1:

     docker exec -it -e VAULT_ADDR=http://127.0.0.1:8200 vault-prod-demo vault login

From here you can repeat the same `vault secrets enable`, `vault kv put`,
`vault policy write`, `vault write auth/approle/...` commands from
vault/scripts/, this time against a server that behaves like a real one.

Clean up when you're done:

     docker rm -f vault-prod-demo
     docker volume rm vault-prod-demo-data
EOF
