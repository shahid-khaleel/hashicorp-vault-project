# app-policy.hcl
#
# The Principle of Least Privilege, expressed as Vault policy: this token
# may READ our demo secrets and nothing else in all of Vault - it cannot
# write, delete, list other apps' secrets, manage auth methods, etc.
#
# In a real org, the Security/Platform team owns and reviews policies like
# this one; the app team just requests access to a path.

# KV v2 stores actual secret data under "data/<path>".
path "secret/data/vault-demo/*" {
  capabilities = ["read"]
}

# KV v2 also exposes "metadata/<path>" - version numbers, timestamps, who
# last changed it. Reading it doesn't reveal secret values, only history.
path "secret/metadata/vault-demo/*" {
  capabilities = ["read", "list"]
}
