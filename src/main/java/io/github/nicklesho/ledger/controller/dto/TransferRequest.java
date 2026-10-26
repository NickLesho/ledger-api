package io.github.nicklesho.ledger.controller.dto;

import io.github.nicklesho.ledger.service.TransferCommand;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) {

    public TransferCommand toCommand(String idempotencyKey) {
        return new TransferCommand(idempotencyKey, fromAccountId, toAccountId, amount);
    }
}