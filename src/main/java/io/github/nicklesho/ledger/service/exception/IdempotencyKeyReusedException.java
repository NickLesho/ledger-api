package io.github.nicklesho.ledger.service.exception;

public class IdempotencyKeyReusedException extends LedgerException {

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey + "' was already used for a different transfer request");
    }
}