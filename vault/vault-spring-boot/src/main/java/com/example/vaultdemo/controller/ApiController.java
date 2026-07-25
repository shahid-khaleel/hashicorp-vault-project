package com.example.vaultdemo.controller;

import com.example.vaultdemo.exception.VaultAuthenticationException;
import com.example.vaultdemo.exception.VaultConnectionException;
import com.example.vaultdemo.service.VaultService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain JSON health check - the kind a container orchestrator or CI
 * smoke test would poll. Deliberately separate from Spring Boot
 * Actuator's /actuator/health (also enabled) so the response shape
 * matches the sibling Python project's /health exactly.
 */
@RestController
public class ApiController {

    private final VaultService vaultService;

    public ApiController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            vaultService.getHealth();
            vaultService.authenticate();
            body.put("status", "ok");
            body.put("vaultReachable", true);
            body.put("vaultAuthenticated", true);
            return ResponseEntity.ok(body);
        } catch (VaultConnectionException | VaultAuthenticationException exc) {
            body.put("status", "degraded");
            body.put("error", exc.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
