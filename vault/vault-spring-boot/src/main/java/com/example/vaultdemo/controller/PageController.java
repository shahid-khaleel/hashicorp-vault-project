package com.example.vaultdemo.controller;

import com.example.vaultdemo.config.AppProperties;
import com.example.vaultdemo.exception.VaultAuthenticationException;
import com.example.vaultdemo.exception.VaultConnectionException;
import com.example.vaultdemo.exception.VaultPermissionDeniedException;
import com.example.vaultdemo.exception.VaultSecretNotFoundException;
import com.example.vaultdemo.service.VaultService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.vault.support.VaultHealth;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * The three UI pages, deliberately mirroring the sibling Python project's
 * app.py routes one-for-one: Home (status), Secrets (live values), Vault
 * Info (connection/token metadata).
 */
@Controller
public class PageController {

    private final VaultService vaultService;
    private final AppProperties props;

    public PageController(VaultService vaultService, AppProperties props) {
        this.vaultService = vaultService;
        this.props = props;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("appName", props.getName());
        model.addAttribute("appEnv", props.getEnv());
        model.addAttribute("vaultAddr", props.getVault().getAddress());
        model.addAttribute("vaultReachable", false);
        model.addAttribute("vaultSealed", (Boolean) null);
        model.addAttribute("authenticated", false);
        model.addAttribute("error", null);

        try {
            VaultHealth health = vaultService.getHealth();
            model.addAttribute("vaultReachable", true);
            model.addAttribute("vaultSealed", health.isSealed());

            vaultService.authenticate();
            model.addAttribute("authenticated", true);
        } catch (VaultConnectionException exc) {
            model.addAttribute("error", "Cannot reach Vault: " + exc.getMessage());
        } catch (VaultAuthenticationException exc) {
            model.addAttribute("error", "Vault authentication failed: " + exc.getMessage());
        }
        return "home";
    }

    @GetMapping("/secrets")
    public String secrets(Model model) {
        model.addAttribute("appName", props.getName());
        model.addAttribute("database", null);
        model.addAttribute("api", null);
        model.addAttribute("appConfig", null);
        model.addAttribute("errorKind", null);
        model.addAttribute("errorMessage", null);

        try {
            Map<String, Object> database = vaultService.readSecret("database");
            Map<String, Object> api = vaultService.readSecret("api");
            Map<String, Object> appConfig = vaultService.readSecret("config");
            model.addAttribute("database", database);
            model.addAttribute("api", api);
            model.addAttribute("appConfig", appConfig);
        } catch (VaultConnectionException exc) {
            model.addAttribute("errorKind", "connection");
            model.addAttribute("errorMessage", exc.getMessage());
        } catch (VaultAuthenticationException exc) {
            model.addAttribute("errorKind", "auth");
            model.addAttribute("errorMessage", exc.getMessage());
        } catch (VaultPermissionDeniedException exc) {
            model.addAttribute("errorKind", "permission");
            model.addAttribute("errorMessage", exc.getMessage());
        } catch (VaultSecretNotFoundException exc) {
            model.addAttribute("errorKind", "not_found");
            model.addAttribute("errorMessage", exc.getMessage());
        }
        return "secrets";
    }

    @GetMapping("/vault-info")
    public String vaultInfo(Model model) {
        model.addAttribute("vaultAddr", props.getVault().getAddress());
        model.addAttribute("authMethod", props.getVault().getAuthMethod());
        model.addAttribute("mountPoint", props.getVault().getMountPoint());
        model.addAttribute("secretPath", props.getVault().getSecretPath());
        model.addAttribute("namespace",
                props.getVault().getNamespace().isEmpty()
                        ? "N/A (Community Edition has no namespaces)"
                        : props.getVault().getNamespace());
        model.addAttribute("tokenInfo", null);
        model.addAttribute("error", null);

        try {
            model.addAttribute("tokenInfo", vaultService.getTokenInfo());
        } catch (VaultConnectionException | VaultAuthenticationException | VaultPermissionDeniedException exc) {
            model.addAttribute("error", exc.getMessage());
        }
        return "vault-info";
    }
}
