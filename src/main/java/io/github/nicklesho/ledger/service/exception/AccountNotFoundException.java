package io.github.nicklesho.ledger.service.exception;

import java.util.UUID;

public class AccountNotFoundException extends LedgerException {

    public AccountNotFoundException(UUID accountId) {
        super("Account " + accountId + " not found");
    }
}