package dev.codearena.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "execution_languages")
public class ExecutionLanguage {

    @Id
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "cached_at", nullable = false)
    private OffsetDateTime cachedAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getCachedAt() { return cachedAt; }
    public void setCachedAt(OffsetDateTime cachedAt) { this.cachedAt = cachedAt; }
}
