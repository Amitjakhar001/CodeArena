package dev.codearena.app.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("submissions")
@CompoundIndexes({
    @CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
})
public class Submission {

    @Id
    private ObjectId id;

    private ObjectId userId;

    @Indexed
    private ObjectId problemId;

    private int languageId;
    private String sourceCode;
    private String customStdin;

    private List<String> executionTokens = new ArrayList<>();

    private String latestStatus;
    private Instant createdAt;
    private Instant updatedAt;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public ObjectId getUserId() { return userId; }
    public void setUserId(ObjectId userId) { this.userId = userId; }

    public ObjectId getProblemId() { return problemId; }
    public void setProblemId(ObjectId problemId) { this.problemId = problemId; }

    public int getLanguageId() { return languageId; }
    public void setLanguageId(int languageId) { this.languageId = languageId; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getCustomStdin() { return customStdin; }
    public void setCustomStdin(String customStdin) { this.customStdin = customStdin; }

    public List<String> getExecutionTokens() { return executionTokens; }
    public void setExecutionTokens(List<String> executionTokens) { this.executionTokens = executionTokens; }

    public String getLatestStatus() { return latestStatus; }
    public void setLatestStatus(String latestStatus) { this.latestStatus = latestStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
