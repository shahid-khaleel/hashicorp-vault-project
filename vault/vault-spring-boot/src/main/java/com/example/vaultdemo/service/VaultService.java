package com.example.vaultdemo.service;

import com.example.vaultdemo.config.AppProperties;
import com.example.vaultdemo.exception.VaultAuthenticationException;
import com.example.vaultdemo.exception.VaultConnectionException;
import com.example.vaultdemo.exception.VaultPermissionDeniedException;
import com.example.vaultdemo.exception.VaultSecretNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

/**
 * The single class in this project that talks to Vault. Everything else
 * (controllers, templates) only ever calls methods here and handles the
 * four exception types below - mirroring the sibling Python project's
 * vault_client.py: one adapter, Vault-specific details contained to one
 * file, translated into plain, project-specific exceptions everywhere
 * else.
 *
 * Unlike the Python client, this deliberately does NOT use Spring Cloud
 * Vault's automatic property-source injection (@Value("${...}") magic
 * pulling secrets straight from Vault at startup). That approach hides
 * exactly the mechanics this project exists to teach. Every Vault call
 * here is explicit: build a VaultTemplate, authenticate, read.
 */
@Service
public class VaultService {

    private static final Logger log = LoggerFactory.getLogger(VaultService.class);

    private final AppProperties props;
    private volatile VaultTemplate vaultTemplate;

    public VaultService(AppProperties props) {
        this.props = props;
    }

    // ------------------------------------------------------------------
    // Building the client / authenticating
    // ------------------------------------------------------------------

    /**
     * Builds the VaultTemplate the first time it's needed. Constructing
     * a VaultTemplate does NOT contact Vault by itself - authentication
     * only happens lazily, the first time an operation that needs a
     * token is invoked (see ensureAuthenticated()). This mirrors
     * hvac.Client(...) in the Python project: cheap to construct, no
     * network call until you actually ask for something.
     */
    private synchronized VaultTemplate getTemplate() {
        if (vaultTemplate == null) {
            vaultTemplate = buildTemplate();
        }
        return vaultTemplate;
    }

    /** Forces the next call to build a fresh template (fresh session/login). */
    public synchronized void reset() {
        vaultTemplate = null;
    }

    private VaultTemplate buildTemplate() {
        VaultEndpoint endpoint;
        try {
            endpoint = VaultEndpoint.from(URI.create(props.getVault().getAddress()));
        } catch (Exception exc) {
            throw new VaultConnectionException("Invalid VAULT_ADDR: " + props.getVault().getAddress(), exc);
        }
        // app.vault.namespace is read but not wired to a header here - Vault
        // namespaces are a Vault Enterprise feature and this project targets
        // Community Edition, same as the sibling Python project. A real
        // Enterprise integration would add an X-Vault-Namespace header via
        // a RestTemplate ClientHttpRequestInterceptor at this point.

        ClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        ClientAuthentication clientAuthentication = buildClientAuthentication(endpoint, requestFactory);
        return new VaultTemplate(endpoint, requestFactory, sessionManagerFor(clientAuthentication));
    }

    private org.springframework.vault.authentication.SessionManager sessionManagerFor(ClientAuthentication auth) {
        // SimpleSessionManager logs in once (lazily, on first use) and
        // caches the resulting token. It does NOT auto-renew - which is
        // fine here because ensureAuthenticated() below detects a dead
        // token on the next call and rebuilds the whole template (a
        // fresh login), the same recovery strategy the Python app uses.
        return new org.springframework.vault.authentication.SimpleSessionManager(auth);
    }

    private ClientAuthentication buildClientAuthentication(VaultEndpoint endpoint, ClientHttpRequestFactory requestFactory) {
        String method = props.getVault().getAuthMethod();
        if ("token".equalsIgnoreCase(method)) {
            if (props.getVault().getToken().isEmpty()) {
                throw new VaultAuthenticationException("app.vault.auth-method=token but app.vault.token is empty");
            }
            return new TokenAuthentication(props.getVault().getToken());
        } else if ("approle".equalsIgnoreCase(method)) {
            if (props.getVault().getRoleId().isEmpty() || props.getVault().getSecretId().isEmpty()) {
                throw new VaultAuthenticationException(
                        "app.vault.auth-method=approle but role-id/secret-id are not set");
            }
            AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                    .roleId(AppRoleAuthenticationOptions.RoleId.provided(props.getVault().getRoleId()))
                    .secretId(AppRoleAuthenticationOptions.SecretId.provided(props.getVault().getSecretId()))
                    .build();
            RestTemplate restTemplate = VaultClients.createRestTemplate(endpoint, requestFactory);
            return new AppRoleAuthentication(options, restTemplate);
        }
        throw new VaultAuthenticationException("Unknown app.vault.auth-method: " + method);
    }

    /**
     * Cheap authenticated call used to prove the current session is
     * still good. Any operation below calls this first; on failure it
     * rebuilds the template (forcing a fresh login) and retries once.
     */
    private <T> T withAuthRetry(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ResourceAccessException exc) {
            throw new VaultConnectionException("Could not reach Vault at " + props.getVault().getAddress(), exc);
        } catch (HttpClientErrorException.Forbidden exc) {
            throw new VaultPermissionDeniedException("Token's policy does not allow this operation: " + exc.getMessage(), exc);
        } catch (HttpClientErrorException.Unauthorized exc) {
            log.warn("Vault session invalid/expired - re-authenticating and retrying once");
            reset();
            try {
                return operation.get();
            } catch (HttpClientErrorException.Unauthorized retryExc) {
                throw new VaultAuthenticationException("Re-authentication failed: " + retryExc.getMessage(), retryExc);
            }
        } catch (org.springframework.vault.VaultException exc) {
            throw new VaultAuthenticationException("Vault authentication failed: " + exc.getMessage(), exc);
        }
    }

    // ------------------------------------------------------------------
    // Status / introspection (Home + Vault Info pages)
    // ------------------------------------------------------------------

    /** Unauthenticated call - works even before login, same as Python's get_seal_status(). */
    public VaultHealth getHealth() {
        try {
            return getTemplate().opsForSys().health();
        } catch (ResourceAccessException exc) {
            throw new VaultConnectionException("Could not reach Vault at " + props.getVault().getAddress(), exc);
        }
    }

    /** Forces an actual authenticated round-trip so login failures surface now, not on first real use. */
    public boolean authenticate() {
        return withAuthRetry(() -> getTemplate().read("auth/token/lookup-self") != null);
    }

    /** Mirrors `vault token lookup` / hvac's auth.token.lookup_self(). */
    public Map<String, Object> getTokenInfo() {
        VaultResponse response = withAuthRetry(() -> getTemplate().read("auth/token/lookup-self"));
        if (response == null || response.getData() == null) {
            throw new VaultAuthenticationException("Token lookup returned no data");
        }
        return response.getData();
    }

    // ------------------------------------------------------------------
    // Secrets
    // ------------------------------------------------------------------

    /**
     * Reads one KV-v2 secret, e.g. relativePath="database" reads
     * secret/data/vault-demo/database (Spring Vault adds the "data/"
     * segment for you - unlike raw hvac calls, you never type it).
     */
    public Map<String, Object> readSecret(String relativePath) {
        String fullPath = props.getVault().getSecretPath() + "/" + relativePath;
        VaultResponse response = withAuthRetry(() -> {
            VaultKeyValueOperations kv = getTemplate().opsForKeyValue(props.getVault().getMountPoint(), KeyValueBackend.KV_2);
            return kv.get(fullPath);
        });
        if (response == null || response.getData() == null) {
            throw new VaultSecretNotFoundException(
                    "No secret found at " + props.getVault().getMountPoint() + "/data/" + fullPath);
        }
        return response.getData();
    }
}
