package io.github.nicklesho.ledger.service;

import io.github.nicklesho.ledger.domain.Transfer;

/**
 * @param replayed true when the idempotency key matched an earlier request and
 *                 the original result is being returned unchanged.
 */
public record TransferResult(Transfer transfer, boolean replayed) {
}