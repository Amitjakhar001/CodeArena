package dev.codearena.auth.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Date;

@Document("refresh_tokens")
public class RefreshToken {

    @Id
    private ObjectId id;

    @Indexed
    private ObjectId userId;

    @Indexed(unique = true)
    private String tokenHash;

    // TTL index — Mongo deletes the document at expiresAt.
    // Stored as java.util.Date because Mongo's TTL monitor reads Date fields directly.
    @Indexed(expireAfterSeconds = 0)
    private Date expiresAt;

    private Instant createdAt;
    private Instant revokedAt;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public ObjectId getUserId() { return userId; }
    public void setUserId(ObjectId userId) { this.userId = userId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Date expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
