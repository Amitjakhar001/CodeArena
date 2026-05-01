package dev.codearena.execution.exception;

public class ExecutionNotFoundException extends RuntimeException {
    public ExecutionNotFoundException(String token) {
        super("Execution not found for token " + token);
    }
}
