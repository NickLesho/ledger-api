package io.github.nicklesho.ledger.service;

import io.github.nicklesho.ledger.domain.Account;
import io.github.nicklesho.ledger.repository.AccountRepository;
import io.github.nicklesho.ledger.repository.TransferRepository;
import io.github.nicklesho.ledger.service.exception.AccountNotFoundException;
import io.github.nicklesho.ledger.service.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, transferRepository);
    }

    @Test
    void createsAccountWithNormalizedCurrencyAndBalance() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account account = accountService.createAccount(
                new CreateAccountCommand("  Nick  ", "cad", new BigDecimal("100")));

        assertThat(account.getOwner()).isEqualTo("Nick");
        assertThat(account.getCurrency()).isEqualTo("CAD");
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void rejectsCurrencyWithoutTwoDecimalPlaces() {
        assertThatThrownBy(() -> accountService.createAccount(
                new CreateAccountCommand("Nick", "JPY", BigDecimal.ZERO)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void rejectsNegativeOpeningBalance() {
        assertThatThrownBy(() -> accountService.createAccount(
                new CreateAccountCommand("Nick", "CAD", new BigDecimal("-1.00"))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void missingAccountThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(id))
                .isInstanceOf(AccountNotFoundException.class);
    }
}