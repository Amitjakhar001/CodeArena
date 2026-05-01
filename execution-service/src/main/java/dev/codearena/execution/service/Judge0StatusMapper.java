package dev.codearena.execution.service;

import dev.codearena.execution.domain.ExecutionStatus;

/**
 * Judge0 status IDs (https://ce.judge0.com/statuses) → CodeArena verdict strings.
 * IDs 1-2 are still in flight; 3 is Accepted; 4 is Wrong Answer; 5 is TLE;
 * 6 is Compilation Error; 7-12 are runtime/sandbox errors; 13-14 are platform errors.
 */
public final class Judge0StatusMapper {

    private Judge0StatusMapper() {}

    public static String map(Integer judge0StatusId) {
        if (judge0StatusId == null) return ExecutionStatus.PENDING;
        return switch (judge0StatusId) {
            case 1, 2 -> ExecutionStatus.PENDING;
            case 3 -> ExecutionStatus.ACCEPTED;
            case 4 -> ExecutionStatus.WRONG_ANSWER;
            case 5 -> ExecutionStatus.TIME_LIMIT_EXCEEDED;
            case 6 -> ExecutionStatus.COMPILATION_ERROR;
            case 7, 8, 9, 10, 11, 12 -> ExecutionStatus.RUNTIME_ERROR;
            default -> ExecutionStatus.INTERNAL_ERROR;
        };
    }

    public static boolean isTerminal(Integer judge0StatusId) {
        return judge0StatusId != null && judge0StatusId >= 3;
    }
}
