package dev.vikas.shortener.web;

import dev.vikas.shortener.domain.AliasUnavailableException;
import dev.vikas.shortener.domain.CodeGenerationException;
import dev.vikas.shortener.domain.InvalidAliasException;
import dev.vikas.shortener.domain.InvalidUrlException;
import dev.vikas.shortener.domain.LinkExpiredException;
import dev.vikas.shortener.domain.LinkNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to an RFC 7807 problem document so clients see one error
 * format. Extending {@link ResponseEntityExceptionHandler} covers the standard
 * Spring MVC errors (malformed JSON, unsupported method, unknown path).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidUrlException.class)
    public ProblemDetail handleInvalidUrl(InvalidUrlException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid URL", ex.getMessage());
    }

    @ExceptionHandler(InvalidAliasException.class)
    public ProblemDetail handleInvalidAlias(InvalidAliasException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid alias", ex.getMessage());
    }

    @ExceptionHandler(AliasUnavailableException.class)
    public ProblemDetail handleAliasUnavailable(AliasUnavailableException ex) {
        return problem(HttpStatus.CONFLICT, "Alias unavailable", ex.getMessage());
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ProblemDetail handleNotFound(LinkNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Link not found", ex.getMessage());
    }

    @ExceptionHandler(LinkExpiredException.class)
    public ProblemDetail handleExpired(LinkExpiredException ex) {
        return problem(HttpStatus.GONE, "Link expired", ex.getMessage());
    }

    @ExceptionHandler(CodeGenerationException.class)
    public ProblemDetail handleCodeGeneration(CodeGenerationException ex) {
        log.error("Short code allocation failed", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Temporarily unable to create link",
                "A short code could not be allocated. Please retry.");
    }

    /** Last resort: log the cause, return nothing internal to the client. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred.");
    }

    /** Adds a per-field {@code errors} map to the standard validation response. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail body = ex.getBody();
        body.setDetail("Request validation failed");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
