package com.pratikdesai.transfers.service;

import com.pratikdesai.transfers.dto.request.TransferRequest;
import com.pratikdesai.transfers.entity.Account;
import com.pratikdesai.transfers.entity.Transaction;
import com.pratikdesai.transfers.exception.AccountNotFoundException;
import com.pratikdesai.transfers.exception.InsufficientBalanceException;
import com.pratikdesai.transfers.exception.InvalidTransferException;
import com.pratikdesai.transfers.repository.AccountRepository;
import com.pratikdesai.transfers.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransferServiceImpl transferService;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    @Captor
    private ArgumentCaptor<Transaction> transactionCaptor;

    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        sourceAccount = Account.builder()
                .accountId(123L)
                .balance(new BigDecimal("500.00"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();

        destinationAccount = Account.builder()
                .accountId(456L)
                .balance(new BigDecimal("100.00"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    @Nested
    @DisplayName("Successful Transfer Tests")
    class SuccessfulTransferTests {

        @Test
        @DisplayName("Should transfer amount successfully between accounts")
        void shouldTransferSuccessfully() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(456L)
                    .amount("100.00")
                    .build();

            when(accountRepository.findByIdWithLock(123L)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByIdWithLock(456L)).thenReturn(Optional.of(destinationAccount));
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

            transferService.transfer(request);

            verify(accountRepository, times(2)).save(accountCaptor.capture());
            verify(transactionRepository).save(transactionCaptor.capture());

            assertThat(sourceAccount.getBalance()).isEqualByComparingTo("400.00");
            assertThat(destinationAccount.getBalance()).isEqualByComparingTo("200.00");

            Transaction savedTransaction = transactionCaptor.getValue();
            assertThat(savedTransaction.getSourceAccountId()).isEqualTo(123L);
            assertThat(savedTransaction.getDestinationAccountId()).isEqualTo(456L);
            assertThat(savedTransaction.getAmount()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Should handle transfer with decimal amounts")
        void shouldHandleDecimalAmounts() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(456L)
                    .amount("100.12345")
                    .build();

            when(accountRepository.findByIdWithLock(123L)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByIdWithLock(456L)).thenReturn(Optional.of(destinationAccount));
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

            transferService.transfer(request);

            assertThat(sourceAccount.getBalance()).isEqualByComparingTo("399.87655");
            assertThat(destinationAccount.getBalance()).isEqualByComparingTo("200.12345");
        }
    }

    @Nested
    @DisplayName("Transfer Validation Tests")
    class TransferValidationTests {

        @Test
        @DisplayName("Should throw exception when transferring to same account")
        void shouldThrowExceptionForSameAccount() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(123L)
                    .amount("100.00")
                    .build();

            assertThatThrownBy(() -> transferService.transfer(request))
                    .isInstanceOf(InvalidTransferException.class)
                    .hasMessageContaining("different");

            verify(accountRepository, never()).findByIdWithLock(any());
        }

        @Test
        @DisplayName("Should throw exception for zero amount")
        void shouldThrowExceptionForZeroAmount() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(456L)
                    .amount("0")
                    .build();

            assertThatThrownBy(() -> transferService.transfer(request))
                    .isInstanceOf(InvalidTransferException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("Should throw exception for negative amount")
        void shouldThrowExceptionForNegativeAmount() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(456L)
                    .amount("-100.00")
                    .build();

            assertThatThrownBy(() -> transferService.transfer(request))
                    .isInstanceOf(InvalidTransferException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("Should throw exception for invalid amount format")
        void shouldThrowExceptionForInvalidAmountFormat() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(456L)
                    .amount("invalid")
                    .build();

            assertThatThrownBy(() -> transferService.transfer(request))
                    .isInstanceOf(InvalidTransferException.class)
                    .hasMessageContaining("Invalid amount format");
        }
    }

    @Nested
    @DisplayName("Account Validation Tests")
    class AccountValidationTests {

        @Test
        @DisplayName("Should throw exception when source account not found")
        void shouldThrowExceptionWhenSourceNotFound() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(999L)
                    .destinationAccountId(456L)
                    .amount("100.00")
                    .build();

            when(accountRepository.findByIdWithLock(456L)).thenReturn(Optional.of(destinationAccount));
            when(accountRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transferService.transfer(request))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw exception when destination account not found")
        void shouldThrowExceptionWhenDestinationNotFound() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(999L)
                    .amount("100.00")
                    .build();

            when(accountRepository.findByIdWithLock(123L)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transferService.transfer(request))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw exception for insufficient balance")
        void shouldThrowExceptionForInsufficientBalance() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(456L)
                    .amount("1000.00")
                    .build();

            when(accountRepository.findByIdWithLock(123L)).thenReturn(Optional.of(sourceAccount));
            when(accountRepository.findByIdWithLock(456L)).thenReturn(Optional.of(destinationAccount));

            assertThatThrownBy(() -> transferService.transfer(request))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("500")
                    .hasMessageContaining("1000");

            verify(accountRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }
    }
}
