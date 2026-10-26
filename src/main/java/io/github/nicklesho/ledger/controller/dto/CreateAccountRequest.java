package io.github.nicklesho.ledger.controller.dto;

import io.github.nicklesho.ledger.service.CreateAccountCommand;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(max = 100) String owner,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO 4217 code") String currency,
        @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal openingBalance) {

    public CreateAccountCommand toCommand() {
        return new CreateAccountCommand(owner, currency, openingBalance);
    }
}