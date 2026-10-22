package io.github.nicklesho.ledger.service.exception;

/** Base type for business errors the API layer maps to HTTP responses. */
public abstract class LedgerException extends RuntimeException {

    protected LedgerException(String message) {
        super(message);
    }
}