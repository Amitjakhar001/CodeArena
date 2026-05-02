package dev.codearena.app.dto;

import dev.codearena.app.domain.Submission;

import java.time.Instant;
import java.util.List;

public record SubmissionDetailResponse(
    String id,
    String problemId,
    int languageId,
    String sourceCode,
    String customStdin,
    String latestStatus,
    List<ExecutionResultDto> executions,
    Instant createdAt,
    Instant updatedAt
) {
    public static SubmissionDetailResponse from(Submission s, List<ExecutionResultDto> executions) {
        return new SubmissionDetailResponse(
            s.getId().toHexString(),
            s.getProblemId() == null ? null : s.getProblemId().toHexString(),
            s.getLanguageId(),
            s.getSourceCode(),
            s.getCustomStdin(),
            s.getLatestStatus(),
            executions,
            s.getCreatedAt(),
            s.getUpdatedAt()
        );
    }
}
