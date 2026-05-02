package dev.codearena.app.domain;

public final class SubmissionStatus {
    public static final String PENDING = "PENDING";
    public static final String QUEUED = "QUEUED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String WRONG_ANSWER = "WRONG_ANSWER";
    public static final String TIME_LIMIT_EXCEEDED = "TIME_LIMIT_EXCEEDED";
    public static final String COMPILATION_ERROR = "COMPILATION_ERROR";
    public static final String RUNTIME_ERROR = "RUNTIME_ERROR";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private SubmissionStatus() {}

    public static boolean isTerminal(String status) {
        if (status == null) return false;
        return switch (status) {
            case ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED,
                 COMPILATION_ERROR, RUNTIME_ERROR, INTERNAL_ERROR -> true;
            default -> false;
        };
    }
}
