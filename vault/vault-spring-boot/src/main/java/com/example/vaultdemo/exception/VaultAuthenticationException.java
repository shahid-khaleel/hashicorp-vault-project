package com.example.vaultdemo.exception;

/** Login to Vault failed: bad role_id/secret_id, bad token, or Vault is sealed. */
public class VaultAuthenticationException extends RuntimeException {
    public VaultAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

    public VaultAuthenticationException(String message) {
        super(message);
    }
}
