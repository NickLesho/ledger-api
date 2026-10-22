package io.github.nicklesho.ledger.service;

import io.github.nicklesho.ledger.domain.Account;
import io.github.nicklesho.ledger.domain.Transfer;
import io.github.nicklesho.ledger.repository.AccountRepository;
import io.github.nicklesho.ledger.repository.TransferRepository;
import io.github.nicklesho.ledger.service.exception.AccountNotFoundException;
import io.github.nicklesho.ledger.service.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final int MAX_OWNER_LENGTH = 100;

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public AccountService(AccountRepository accountRepository, TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional
    public Account createAccount(CreateAccountCommand command) {
        String owner = validateOwner(command.owner());
        String currency = validateCurrency(command.currency());
        BigDecimal openingBalance = Amounts.requireNonNegative(
                command.openingBalance() == null ? BigDecimal.ZERO : command.openingBalance(),
                "openingBalance");

        Account account = accountRepository.save(new Account(owner, currency, openingBalance));
        log.info("Created account {} in {}", account.getId(), currency);
        return account;
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Transactional(readOnly = true)
    public Page<Transfer> getTransfers(UUID accountId, Pageable pageable) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return transferRepository.findByAccountId(accountId, pageable);
    }

    private static String validateOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            throw new InvalidRequestException("owner must not be blank");
        }
        String trimmed = owner.strip();
        if (trimmed.length() > MAX_OWNER_LENGTH) {
            throw new InvalidRequestException("owner must be at most " + MAX_OWNER_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String validateCurrency(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidRequestException("currency must not be blank");
        }
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        Currency currency;
        try {
            currency = Currency.getInstance(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown currency code: " + code);
        }
        // Balances are stored with 2 decimal places, so currencies like JPY (0) or BHD (3) don't fit.
        if (currency.getDefaultFractionDigits() != 2) {
            throw new InvalidRequestException("Currency " + normalized + " is not supported");
        }
        return normalized;
    }
}