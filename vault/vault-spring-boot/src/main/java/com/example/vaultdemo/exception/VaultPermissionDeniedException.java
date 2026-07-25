package com.example.vaultdemo.exception;

/** The token authenticated fine, but its attached policy doesn't grant this path. */
public class VaultPermissionDeniedException extends RuntimeException {
    public VaultPermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
