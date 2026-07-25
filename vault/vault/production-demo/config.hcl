# Minimal non-dev Vault server config, used ONLY by
# vault/scripts/production-mode-demo.sh to demonstrate init/unseal.
#
# tls_disable is only acceptable here because this is a throwaway local
# container talking to itself on localhost. A real Vault server always
# terminates TLS.

storage "file" {
  path = "/vault/file"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = "true"
}

disable_mlock = true
ui            = true
