package dev.codearena.execution.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "executions")
public class Execution {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "judge0_token", nullable = false, unique = true, length = 64)
    private String judge0Token;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "submission_id", length = 64)
    private String submissionId;

    @Column(name = "language_id", nullable = false)
    private Integer languageId;

    @Column(name = "source_code_hash", nullable = false, columnDefinition = "char(64)")
    private String sourceCodeHash;

    @Column(columnDefinition = "text")
    private String stdin;

    @Column(name = "expected_output", columnDefinition = "text")
    private String expectedOutput;

    @Column(name = "cpu_time_limit", nullable = false, precision = 5, scale = 2)
    private BigDecimal cpuTimeLimit;

    @Column(name = "memory_limit", nullable = false)
    private Integer memoryLimit;

    @Column(name = "status_id")
    private Integer statusId;

    @Column(name = "status_description", length = 50)
    private String statusDescription;

    @Column(columnDefinition = "text")
    private String stdout;

    @Column(columnDefinition = "text")
    private String stderr;

    @Column(name = "compile_output", columnDefinition = "text")
    private String compileOutput;

    @Column(name = "time_ms")
    private Integer timeMs;

    @Column(name = "memory_kb")
    private Integer memoryKb;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getJudge0Token() { return judge0Token; }
    public void setJudge0Token(String judge0Token) { this.judge0Token = judge0Token; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

    public Integer getLanguageId() { return languageId; }
    public void setLanguageId(Integer languageId) { this.languageId = languageId; }

    public String getSourceCodeHash() { return sourceCodeHash; }
    public void setSourceCodeHash(String sourceCodeHash) { this.sourceCodeHash = sourceCodeHash; }

    public String getStdin() { return stdin; }
    public void setStdin(String stdin) { this.stdin = stdin; }

    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

    public BigDecimal getCpuTimeLimit() { return cpuTimeLimit; }
    public void setCpuTimeLimit(BigDecimal cpuTimeLimit) { this.cpuTimeLimit = cpuTimeLimit; }

    public Integer getMemoryLimit() { return memoryLimit; }
    public void setMemoryLimit(Integer memoryLimit) { this.memoryLimit = memoryLimit; }

    public Integer getStatusId() { return statusId; }
    public void setStatusId(Integer statusId) { this.statusId = statusId; }

    public String getStatusDescription() { return statusDescription; }
    public void setStatusDescription(String statusDescription) { this.statusDescription = statusDescription; }

    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }

    public String getStderr() { return stderr; }
    public void setStderr(String stderr) { this.stderr = stderr; }

    public String getCompileOutput() { return compileOutput; }
    public void setCompileOutput(String compileOutput) { this.compileOutput = compileOutput; }

    public Integer getTimeMs() { return timeMs; }
    public void setTimeMs(Integer timeMs) { this.timeMs = timeMs; }

    public Integer getMemoryKb() { return memoryKb; }
    public void setMemoryKb(Integer memoryKb) { this.memoryKb = memoryKb; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
