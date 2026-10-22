package io.github.nicklesho.ledger.service;

import java.math.BigDecimal;

public record CreateAccountCommand(String owner, String currency, BigDecimal openingBalance) {
}