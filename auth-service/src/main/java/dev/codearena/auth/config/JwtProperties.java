package dev.codearena.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jwt")
public record JwtProperties(
        String secret,
        String issuer,
        int accessTokenTtlMinutes,
        int refreshTokenTtlDays
) {}
