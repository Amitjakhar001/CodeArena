package dev.codearena.auth.exception;

public class MissingAuthHeaderException extends AuthException {
    public MissingAuthHeaderException() {
        super("Missing or malformed Authorization header");
    }
}
