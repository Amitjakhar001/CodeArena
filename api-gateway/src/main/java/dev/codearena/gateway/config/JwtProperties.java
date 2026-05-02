package dev.codearena.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Must hold the same HS256 secret that auth-service signs with so the gateway
 * can verify tokens. Keep both services pointed at the same env var in prod.
 */
@ConfigurationProperties("jwt")
public record JwtProperties(String secret, String issuer) {}
