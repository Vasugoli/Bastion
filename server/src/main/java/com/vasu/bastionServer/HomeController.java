package com.vasu.bastionServer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HomeController {

    private final long startTime = System.currentTimeMillis();

    @Value("${spring.mode:development}")
    private String environment;

    @Value("${app.version:1.0.0}")
    private String version;

    @GetMapping("/health")
    public Map<String, Object> health() {

        long uptime = (System.currentTimeMillis() - startTime) / 1000;

        return Map.of(
            "status", "ok",
            "service", "Bastion-api",
            "version", version,
            "environment", environment,
            "timestamp", Instant.now().toString(),
            "uptime", uptime,
            "checks", Map.of(
                "database", Map.of(
                    "status", "not yet setted",
                    "latency_ms", 0
                ),
                "redis", Map.of(
                    "status", "not yet setted",
                    "latency_ms", 0
                )
            )
        );
    }
}