package dev.codearena.app.exception;

public class MissingUserHeaderException extends RuntimeException {
    public MissingUserHeaderException(String message) { super(message); }
}
