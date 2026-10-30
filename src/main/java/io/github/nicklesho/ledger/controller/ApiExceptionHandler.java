package io.github.nicklesho.ledger.controller;

import io.github.nicklesho.ledger.service.exception.AccountNotFoundException;
import io.github.nicklesho.ledger.service.exception.CurrencyMismatchException;
import io.github.nicklesho.ledger.service.exception.IdempotencyKeyReusedException;
import io.github.nicklesho.ledger.service.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps every error to an RFC 9457 ProblemDetail. Extending ResponseEntityExceptionHandler
 * means Spring's own errors (malformed JSON, missing header, bad UUID, unknown path) use
 * the same format as our business errors.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        return ApiProblems.create(HttpStatus.NOT_FOUND, "account-not-found", "Account not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail handleCurrencyMismatch(CurrencyMismatchException ex) {
        return ApiProblems.create(ApiProblems.UNPROCESSABLE, "currency-mismatch", "Currency mismatch", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail handleIdempotencyKeyReused(IdempotencyKeyReusedException ex) {
        return ApiProblems.create(HttpStatus.CONFLICT, "idempotency-key-reused", "Idempotency key reused", ex.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ProblemDetail handleConcurrencyFailure(RuntimeException ex) {
        log.warn("Concurrency conflict: {}", ex.getMessage());
        return ApiProblems.create(HttpStatus.CONFLICT, "concurrency-conflict", "Concurrency conflict",
                "The resource was modified concurrently. Retry the request with the same Idempotency-Key.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        // Log the details server-side; never leak internals (stack traces, SQL) to the client.
        log.error("Unhandled error", ex);
        return ApiProblems.create(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
                "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid")))
                .toList();

        ProblemDetail problem = ApiProblems.create(HttpStatus.BAD_REQUEST, "validation-failed",
                "Validation failed", "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }
}