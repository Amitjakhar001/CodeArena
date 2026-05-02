package dev.codearena.app.dto;

import dev.codearena.app.domain.Submission;

import java.time.Instant;

public record SubmissionSummaryResponse(
    String id,
    String problemId,
    int languageId,
    String latestStatus,
    Instant createdAt,
    Instant updatedAt
) {
    public static SubmissionSummaryResponse from(Submission s) {
        return new SubmissionSummaryResponse(
            s.getId().toHexString(),
            s.getProblemId() == null ? null : s.getProblemId().toHexString(),
            s.getLanguageId(),
            s.getLatestStatus(),
            s.getCreatedAt(),
            s.getUpdatedAt()
        );
    }
}
