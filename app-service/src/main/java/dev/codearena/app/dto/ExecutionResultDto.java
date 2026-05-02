package dev.codearena.app.dto;

import dev.codearena.proto.execution.v1.ExecutionResult;

public record ExecutionResultDto(
    String executionToken,
    String status,
    String statusDescription,
    String stdout,
    String stderr,
    String compileOutput,
    int timeMs,
    int memoryKb,
    long createdAtEpochSeconds,
    long completedAtEpochSeconds
) {
    public static ExecutionResultDto from(ExecutionResult r) {
        return new ExecutionResultDto(
            r.getExecutionToken(),
            r.getStatus(),
            r.getStatusDescription(),
            r.getStdout(),
            r.getStderr(),
            r.getCompileOutput(),
            r.getTimeMs(),
            r.getMemoryKb(),
            r.getCreatedAtEpochSeconds(),
            r.getCompletedAtEpochSeconds()
        );
    }
}
