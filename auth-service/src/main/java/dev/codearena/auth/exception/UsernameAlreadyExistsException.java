package dev.codearena.auth.exception;

public class UsernameAlreadyExistsException extends AuthException {
    public UsernameAlreadyExistsException(String username) {
        super("Username already taken: " + username);
    }
}
