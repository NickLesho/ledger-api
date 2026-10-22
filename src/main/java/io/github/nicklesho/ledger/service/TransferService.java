package io.github.nicklesho.ledger.service;

import io.github.nicklesho.ledger.domain.Account;
import io.github.nicklesho.ledger.domain.Transfer;
import io.github.nicklesho.ledger.repository.AccountRepository;
import io.github.nicklesho.ledger.repository.TransferRepository;
import io.github.nicklesho.ledger.service.exception.AccountNotFoundException;
import io.github.nicklesho.ledger.service.exception.CurrencyMismatchException;
import io.github.nicklesho.ledger.service.exception.IdempotencyKeyReusedException;
import io.github.nicklesho.ledger.service.exception.InvalidRequestException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final String IDEMPOTENCY_KEY_CONSTRAINT = "uq_transfer_idempotency_key";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional
    public TransferResult transfer(TransferCommand command) {
        validate(command);
        BigDecimal amount = Amounts.requirePositive(command.amount(), "amount");

        // Fast path: a retry of a request we've already processed.
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), command, amount);
        }

        LockedAccounts accounts = lockInIdOrder(command.fromAccountId(), command.toAccountId());

        // A concurrent request with the same key may have committed while we waited for the locks.
        existing = transferRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), command, amount);
        }

        Account from = accounts.from();
        Account to = accounts.to();
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new CurrencyMismatchException(from.getCurrency(), to.getCurrency());
        }

        Transfer transfer;
        if (from.hasSufficientFunds(amount)) {
            from.debit(amount);
            to.credit(amount);
            transfer = Transfer.completed(command.idempotencyKey(), from.getId(), to.getId(), amount);
        } else {
            transfer = Transfer.rejected(command.idempotencyKey(), from.getId(), to.getId(), amount);
        }

        Transfer saved = persist(transfer);
        log.info("Transfer {} {}: {} {} from {} to {}", saved.getId(), saved.getStatus(),
                amount, from.getCurrency(), from.getId(), to.getId());
        return new TransferResult(saved, false);
    }

    private static void validate(TransferCommand command) {
        String key = command.idempotencyKey();
        if (key == null || key.isBlank()) {
            throw new InvalidRequestException("Idempotency-Key must not be blank");
        }
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidRequestException("Idempotency-Key must be at most "
                    + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        if (command.fromAccountId() == null || command.toAccountId() == null) {
            throw new InvalidRequestException("fromAccountId and toAccountId are required");
        }
        if (command.fromAccountId().equals(command.toAccountId())) {
            throw new InvalidRequestException("Source and destination accounts must be different");
        }
    }

    /**
     * Locks both rows in a fixed global order (ascending UUID), whatever the transfer
     * direction. Two transfers A->B and B->A then both lock the same row first, so they
     * queue on it instead of each holding one lock and waiting on the other (deadlock).
     */
    private LockedAccounts lockInIdOrder(UUID fromId, UUID toId) {
        boolean fromFirst = fromId.compareTo(toId) < 0;
        Account first = lock(fromFirst ? fromId : toId);
        Account second = lock(fromFirst ? toId : fromId);
        return fromFirst ? new LockedAccounts(first, second) : new LockedAccounts(second, first);
    }

    private Account lock(UUID accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private static TransferResult replay(Transfer existing, TransferCommand command, BigDecimal amount) {
        if (!existing.matchesRequest(command.fromAccountId(), command.toAccountId(), amount)) {
            throw new IdempotencyKeyReusedException(command.idempotencyKey());
        }
        log.info("Replaying transfer {} for idempotency key {}", existing.getId(), command.idempotencyKey());
        return new TransferResult(existing, true);
    }

    /**
     * Flushes inside the method so a unique-key race surfaces here, where it can be
     * translated, rather than at commit time.
     */
    private Transfer persist(Transfer transfer) {
        try {
            return transferRepository.saveAndFlush(transfer);
        } catch (DataIntegrityViolationException e) {
            if (isIdempotencyKeyViolation(e)) {
                throw new IdempotencyKeyReusedException(transfer.getIdempotencyKey());
            }
            throw e;
        }
    }

    private static boolean isIdempotencyKeyViolation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve
                    && IDEMPOTENCY_KEY_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private record LockedAccounts(Account from, Account to) {
    }
}