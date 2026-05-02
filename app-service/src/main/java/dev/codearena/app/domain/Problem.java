package dev.codearena.app.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("problems")
public class Problem {

    @Id
    private ObjectId id;

    @Indexed(unique = true)
    private String slug;

    private String title;
    private String description;
    private Difficulty difficulty;
    private List<SampleTestCase> sampleTestCases;
    private List<Integer> supportedLanguages;
    private Instant createdAt;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }

    public List<SampleTestCase> getSampleTestCases() { return sampleTestCases; }
    public void setSampleTestCases(List<SampleTestCase> sampleTestCases) { this.sampleTestCases = sampleTestCases; }

    public List<Integer> getSupportedLanguages() { return supportedLanguages; }
    public void setSupportedLanguages(List<Integer> supportedLanguages) { this.supportedLanguages = supportedLanguages; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
