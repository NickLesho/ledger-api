package io.github.nicklesho.ledger.service;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(String idempotencyKey, UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
}