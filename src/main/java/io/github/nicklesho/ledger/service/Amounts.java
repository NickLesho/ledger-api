package io.github.nicklesho.ledger.service;

import io.github.nicklesho.ledger.service.exception.InvalidRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Validates and normalizes money amounts to the NUMERIC(19,2) column format. */
final class Amounts {

    private static final int SCALE = 2;
    private static final int MAX_PRECISION = 19;

    private Amounts() {
    }

    static BigDecimal requirePositive(BigDecimal amount, String field) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidRequestException(field + " must be greater than zero");
        }
        return normalize(amount, field);
    }

    static BigDecimal requireNonNegative(BigDecimal amount, String field) {
        if (amount == null || amount.signum() < 0) {
            throw new InvalidRequestException(field + " must not be negative");
        }
        return normalize(amount, field);
    }

    private static BigDecimal normalize(BigDecimal amount, String field) {
        BigDecimal scaled;
        try {
            // UNNECESSARY: never round money silently; reject anything finer than cents.
            scaled = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidRequestException(field + " must have at most " + SCALE + " decimal places");
        }
        if (scaled.precision() > MAX_PRECISION) {
            throw new InvalidRequestException(field + " is too large");
        }
        return scaled;
    }
}