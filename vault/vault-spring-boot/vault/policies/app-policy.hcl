# app-policy.hcl
#
# Identical intent to the sibling Python project's policy: least
# privilege, read-only access to exactly this app's secrets, nothing else.

path "secret/data/vault-demo/*" {
  capabilities = ["read"]
}

path "secret/metadata/vault-demo/*" {
  capabilities = ["read", "list"]
}
