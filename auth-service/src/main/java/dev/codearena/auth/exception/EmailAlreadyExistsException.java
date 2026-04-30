package dev.codearena.auth.exception;

public class EmailAlreadyExistsException extends AuthException {
    public EmailAlreadyExistsException(String email) {
        super("Email already in use: " + email);
    }
}
