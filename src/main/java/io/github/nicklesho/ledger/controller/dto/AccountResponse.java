package io.github.nicklesho.ledger.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.nicklesho.ledger.domain.Account;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String owner,
        String currency,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getOwner(), account.getCurrency(), account.getBalance());
    }
}