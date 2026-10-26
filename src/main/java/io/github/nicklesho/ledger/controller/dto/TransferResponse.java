package io.github.nicklesho.ledger.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.nicklesho.ledger.domain.Transfer;
import io.github.nicklesho.ledger.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        TransferStatus status,
        Instant createdAt) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getFromAccountId(), transfer.getToAccountId(),
                transfer.getAmount(), transfer.getStatus(), transfer.getCreatedAt());
    }
}