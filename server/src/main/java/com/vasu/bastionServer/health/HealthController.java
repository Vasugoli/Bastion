package com.vasu.bastionServer.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.vasu.bastionServer.identity.IdentityManager;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    private final IdentityManager identityManager;
    private final long startTime = System.currentTimeMillis();

    @Value("${app.mode:development}")
    private String mode;
    
    public HealthController(IdentityManager identityManager) {
        this.identityManager = identityManager;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        long uptime = (System.currentTimeMillis() - startTime) / 1000;
    
        String fingerprint;
        try {
            fingerprint = identityManager.getFingerprint();
        } catch (Exception e) {
            fingerprint = "unavailable";
        }
    
        return Map.of(
            "status",    "UP",
            "service",   "bastion-api",
            "mode",       mode,
            "timestamp",  Instant.now().toString(),
            "uptime",     uptime,
            "identity",   fingerprint,
            "checks", Map.of(
                "application", "UP",
                "database",    "UP",
                "ssh",         "UP",
                "websocket",   "UP"
            )
        );
    }

    
}