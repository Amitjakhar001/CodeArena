package dev.codearena.execution.exception;

public class ExecutionForbiddenException extends RuntimeException {
    public ExecutionForbiddenException() {
        super("Caller does not own this execution");
    }
}
