package dev.codearena.execution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("judge0")
public record Judge0Properties(
        String baseUrl,
        String webhookBaseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        int recoveryStuckSeconds
) {}
