package com.example.vaultdemo.exception;

/** Vault is completely unreachable: network/DNS issue, wrong address, container still starting. */
public class VaultConnectionException extends RuntimeException {
    public VaultConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public VaultConnectionException(String message) {
        super(message);
    }
}
