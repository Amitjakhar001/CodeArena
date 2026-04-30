package dev.codearena.auth.exception;

public class InvalidCredentialsException extends AuthException {
    public InvalidCredentialsException() {
        super("Invalid email/username or password");
    }
}
