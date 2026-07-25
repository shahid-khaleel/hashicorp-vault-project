package com.example.vaultdemo.exception;

/** The token authenticated fine, but nothing exists at the requested path. */
public class VaultSecretNotFoundException extends RuntimeException {
    public VaultSecretNotFoundException(String message) {
        super(message);
    }
}
