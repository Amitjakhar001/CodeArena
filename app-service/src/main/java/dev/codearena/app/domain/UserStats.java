package dev.codearena.app.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Document("user_stats")
public class UserStats {

    @Id
    private ObjectId id;

    @Indexed(unique = true)
    private ObjectId userId;

    private long totalSubmissions;
    private long acceptedSubmissions;

    /** Keyed by Judge0 language id (as String, since Mongo field names must be strings). */
    private Map<String, LanguageStat> byLanguage = new HashMap<>();

    private Instant lastSubmissionAt;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public ObjectId getUserId() { return userId; }
    public void setUserId(ObjectId userId) { this.userId = userId; }

    public long getTotalSubmissions() { return totalSubmissions; }
    public void setTotalSubmissions(long totalSubmissions) { this.totalSubmissions = totalSubmissions; }

    public long getAcceptedSubmissions() { return acceptedSubmissions; }
    public void setAcceptedSubmissions(long acceptedSubmissions) { this.acceptedSubmissions = acceptedSubmissions; }

    public Map<String, LanguageStat> getByLanguage() { return byLanguage; }
    public void setByLanguage(Map<String, LanguageStat> byLanguage) { this.byLanguage = byLanguage; }

    public Instant getLastSubmissionAt() { return lastSubmissionAt; }
    public void setLastSubmissionAt(Instant lastSubmissionAt) { this.lastSubmissionAt = lastSubmissionAt; }
}
