package com.example.vaultdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Java equivalent of the Python project's config.py: reads every
 * *non-secret* setting from application.yml / environment variables
 * (Spring Boot maps APP_VAULT_ROLE_ID etc. onto app.vault.role-id
 * automatically via its relaxed binding rules). The actual secret
 * *values* are never here - only connection details and (for AppRole)
 * the credentials needed to go ask Vault for them.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name = "Vault Demo App (Spring Boot)";
    private String env = "development";
    private final Vault vault = new Vault();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public Vault getVault() {
        return vault;
    }

    public static class Vault {
        private String address = "http://vault:8200";
        private String namespace = "";
        /** "approle" (default, machine identity) or "token" (local experimentation). */
        private String authMethod = "approle";
        private String token = "";
        private String roleId = "";
        private String secretId = "";
        private String mountPoint = "secret";
        private String secretPath = "vault-demo";

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getAuthMethod() {
            return authMethod;
        }

        public void setAuthMethod(String authMethod) {
            this.authMethod = authMethod;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getRoleId() {
            return roleId;
        }

        public void setRoleId(String roleId) {
            this.roleId = roleId;
        }

        public String getSecretId() {
            return secretId;
        }

        public void setSecretId(String secretId) {
            this.secretId = secretId;
        }

        public String getMountPoint() {
            return mountPoint;
        }

        public void setMountPoint(String mountPoint) {
            this.mountPoint = mountPoint;
        }

        public String getSecretPath() {
            return secretPath;
        }

        public void setSecretPath(String secretPath) {
            this.secretPath = secretPath;
        }
    }
}
