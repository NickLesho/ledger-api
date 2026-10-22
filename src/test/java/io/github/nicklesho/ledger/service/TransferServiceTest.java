package io.github.nicklesho.ledger.service;

import io.github.nicklesho.ledger.domain.Account;
import io.github.nicklesho.ledger.domain.Transfer;
import io.github.nicklesho.ledger.domain.TransferStatus;
import io.github.nicklesho.ledger.repository.AccountRepository;
import io.github.nicklesho.ledger.repository.TransferRepository;
import io.github.nicklesho.ledger.service.exception.CurrencyMismatchException;
import io.github.nicklesho.ledger.service.exception.IdempotencyKeyReusedException;
import io.github.nicklesho.ledger.service.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String KEY = "key-123";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(accountRepository, transferRepository);
    }

    @Test
    void completedTransferMovesMoneyBetweenAccounts() {
        Account from = account(LOW_ID, "CAD", "100.00");
        Account to = account(HIGH_ID, "CAD", "0.00");
        stubLocks(from, to);
        stubSaveReturnsArgument();

        TransferResult result = transferService.transfer(command(LOW_ID, HIGH_ID, "30.00"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(from.getBalance()).isEqualByComparingTo("70.00");
        assertThat(to.getBalance()).isEqualByComparingTo("30.00");
    }

    @Test
    void locksAccountsInIdOrderRegardlessOfDirection() {
        Account low = account(LOW_ID, "CAD", "50.00");
        Account high = account(HIGH_ID, "CAD", "50.00");
        stubLocks(low, high);
        stubSaveReturnsArgument();

        // Transfer runs HIGH -> LOW, but LOW must still be locked first.
        transferService.transfer(command(HIGH_ID, LOW_ID, "10.00"));

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByIdForUpdate(LOW_ID);
        order.verify(accountRepository).findByIdForUpdate(HIGH_ID);
        assertThat(high.getBalance()).isEqualByComparingTo("40.00");
        assertThat(low.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void insufficientFundsRecordsRejectedTransferAndLeavesBalancesUntouched() {
        Account from = account(LOW_ID, "CAD", "10.00");
        Account to = account(HIGH_ID, "CAD", "0.00");
        stubLocks(from, to);
        stubSaveReturnsArgument();

        TransferResult result = transferService.transfer(command(LOW_ID, HIGH_ID, "30.00"));

        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(from.getBalance()).isEqualByComparingTo("10.00");
        assertThat(to.getBalance()).isEqualByComparingTo("0.00");
        verify(transferRepository).saveAndFlush(any(Transfer.class));
    }

    @Test
    void repeatedKeyWithSamePayloadReturnsOriginalWithoutLocking() {
        Transfer original = Transfer.completed(KEY, LOW_ID, HIGH_ID, new BigDecimal("30.00"));
        when(transferRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(original));

        TransferResult result = transferService.transfer(command(LOW_ID, HIGH_ID, "30.0"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.transfer()).isSameAs(original);
        verifyNoInteractions(accountRepository);
        verify(transferRepository, never()).saveAndFlush(any());
    }

    @Test
    void repeatedKeyWithDifferentPayloadIsRejected() {
        Transfer original = Transfer.completed(KEY, LOW_ID, HIGH_ID, new BigDecimal("30.00"));
        when(transferRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> transferService.transfer(command(LOW_ID, HIGH_ID, "99.00")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void keyCommittedByConcurrentRequestWhileWaitingForLocksIsReplayed() {
        Account from = account(LOW_ID, "CAD", "100.00");
        Account to = account(HIGH_ID, "CAD", "0.00");
        stubLocks(from, to);
        Transfer committedMeanwhile = Transfer.completed(KEY, LOW_ID, HIGH_ID, new BigDecimal("30.00"));
        when(transferRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty(), Optional.of(committedMeanwhile));

        TransferResult result = transferService.transfer(command(LOW_ID, HIGH_ID, "30.00"));

        assertThat(result.replayed()).isTrue();
        assertThat(from.getBalance()).isEqualByComparingTo("100.00");
        verify(transferRepository, never()).saveAndFlush(any());
    }

    @Test
    void currencyMismatchIsRejectedAndNothingIsSaved() {
        stubLocks(account(LOW_ID, "CAD", "100.00"), account(HIGH_ID, "USD", "0.00"));

        assertThatThrownBy(() -> transferService.transfer(command(LOW_ID, HIGH_ID, "10.00")))
                .isInstanceOf(CurrencyMismatchException.class);
        verify(transferRepository, never()).saveAndFlush(any());
    }

    @Test
    void transferToSameAccountIsRejected() {
        assertThatThrownBy(() -> transferService.transfer(command(LOW_ID, LOW_ID, "10.00")))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(accountRepository, transferRepository);
    }

    @Test
    void amountWithMoreThanTwoDecimalsIsRejected() {
        assertThatThrownBy(() -> transferService.transfer(command(LOW_ID, HIGH_ID, "10.001")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("decimal places");
    }

    @Test
    void nonPositiveAmountIsRejected() {
        assertThatThrownBy(() -> transferService.transfer(command(LOW_ID, HIGH_ID, "0.00")))
                .isInstanceOf(InvalidRequestException.class);
    }

    private static TransferCommand command(UUID from, UUID to, String amount) {
        return new TransferCommand(KEY, from, to, new BigDecimal(amount));
    }

    private static Account account(UUID id, String currency, String balance) {
        Account account = new Account("Test Owner", currency, new BigDecimal(balance));
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private void stubLocks(Account first, Account second) {
        when(accountRepository.findByIdForUpdate(first.getId())).thenReturn(Optional.of(first));
        when(accountRepository.findByIdForUpdate(second.getId())).thenReturn(Optional.of(second));
    }

    private void stubSaveReturnsArgument() {
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
    }
}