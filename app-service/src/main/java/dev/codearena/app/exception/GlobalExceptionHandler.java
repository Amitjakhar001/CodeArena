package dev.codearena.app.exception;

import dev.codearena.app.grpc.GrpcUpstreamException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://codearena.dev/errors/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> Map.of(
                "field", e.getField(),
                "message", e.getDefaultMessage() == null ? "invalid" : e.getDefaultMessage()))
            .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation Failed",
            "Request body validation failed", req, "validation");
        pd.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(UnsupportedLanguageException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedLanguage(UnsupportedLanguageException ex, HttpServletRequest req) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Unsupported Language", ex.getMessage(), req, "unsupported-language");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(MissingUserHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingUser(MissingUserHeaderException ex, HttpServletRequest req) {
        ProblemDetail pd = problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), req, "unauthorized");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
    }

    @ExceptionHandler(SubmissionForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(SubmissionForbiddenException ex, HttpServletRequest req) {
        ProblemDetail pd = problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), req, "forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
    }

    @ExceptionHandler({ProblemNotFoundException.class, SubmissionNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        ProblemDetail pd = problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req, "not-found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(GrpcUpstreamException.class)
    public ResponseEntity<ProblemDetail> handleGrpcUpstream(GrpcUpstreamException ex, HttpServletRequest req) {
        log.error("gRPC upstream call failed for {}", req.getRequestURI(), ex);
        ProblemDetail pd = problem(HttpStatus.BAD_GATEWAY, "Upstream Service Unavailable",
            "A dependent service is currently unavailable", req, "upstream-unavailable");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(pd);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req, "bad-request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {}", req.getRequestURI(), ex);
        ProblemDetail pd = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
            "An unexpected error occurred", req, "internal");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail,
                                  HttpServletRequest req, String typeSlug) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        pd.setTitle(title);
        pd.setType(URI.create(TYPE_BASE + typeSlug));
        pd.setInstance(URI.create(req.getRequestURI()));
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            pd.setProperty("correlationId", correlationId);
        }
        return pd;
    }
}
