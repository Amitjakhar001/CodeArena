package dev.codearena.execution.exception;

public class UnsupportedLanguageException extends RuntimeException {
    public UnsupportedLanguageException(int languageId) {
        super("Language id " + languageId + " is not supported");
    }
}
