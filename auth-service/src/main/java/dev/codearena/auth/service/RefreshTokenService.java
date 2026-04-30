package dev.codearena.auth.service;

import dev.codearena.auth.config.JwtProperties;
import dev.codearena.auth.domain.RefreshToken;
import dev.codearena.auth.exception.InvalidRefreshTokenException;
import dev.codearena.auth.repository.RefreshTokenRepository;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();

    private final RefreshTokenRepository repo;
    private final JwtProperties props;

    public RefreshTokenService(RefreshTokenRepository repo, JwtProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public Issued issue(ObjectId userId) {
        byte[] raw = new byte[32];
        RNG.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken doc = new RefreshToken();
        doc.setUserId(userId);
        doc.setTokenHash(sha256Hex(token));
        Instant now = Instant.now();
        doc.setCreatedAt(now);
        doc.setExpiresAt(Date.from(now.plus(props.refreshTokenTtlDays(), ChronoUnit.DAYS)));
        repo.save(doc);

        return new Issued(token, doc);
    }

    public RefreshToken validate(String token) {
        RefreshToken doc = repo.findByTokenHash(sha256Hex(token))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        if (doc.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException("Refresh token has been revoked");
        }
        if (doc.getExpiresAt().toInstant().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }
        return doc;
    }

    public void revoke(String token) {
        repo.findByTokenHash(sha256Hex(token)).ifPresent(doc -> {
            if (doc.getRevokedAt() == null) {
                doc.setRevokedAt(Instant.now());
                repo.save(doc);
            }
        });
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }

    public record Issued(String rawToken, RefreshToken document) {}
}
