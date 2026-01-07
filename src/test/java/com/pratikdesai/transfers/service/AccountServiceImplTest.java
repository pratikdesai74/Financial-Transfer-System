package com.pratikdesai.transfers.service;

import com.pratikdesai.transfers.dto.request.CreateAccountRequest;
import com.pratikdesai.transfers.dto.response.AccountResponse;
import com.pratikdesai.transfers.entity.Account;
import com.pratikdesai.transfers.exception.AccountAlreadyExistsException;
import com.pratikdesai.transfers.exception.AccountNotFoundException;
import com.pratikdesai.transfers.exception.InvalidTransferException;
import com.pratikdesai.transfers.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountServiceImpl accountService;

    @Nested
    @DisplayName("Create Account Tests")
    class CreateAccountTests {

        @Test
        @DisplayName("Should create account successfully with valid data")
        void shouldCreateAccountSuccessfully() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountId(123L)
                    .initialBalance("100.50")
                    .build();

            when(accountRepository.existsById(123L)).thenReturn(false);
            when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

            accountService.createAccount(request);

            verify(accountRepository).existsById(123L);
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("Should throw exception when account already exists")
        void shouldThrowExceptionWhenAccountExists() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountId(123L)
                    .initialBalance("100.50")
                    .build();

            when(accountRepository.existsById(123L)).thenReturn(true);

            assertThatThrownBy(() -> accountService.createAccount(request))
                    .isInstanceOf(AccountAlreadyExistsException.class)
                    .hasMessageContaining("123");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception for negative initial balance")
        void shouldThrowExceptionForNegativeBalance() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountId(123L)
                    .initialBalance("-100.50")
                    .build();

            when(accountRepository.existsById(123L)).thenReturn(false);

            assertThatThrownBy(() -> accountService.createAccount(request))
                    .isInstanceOf(InvalidTransferException.class)
                    .hasMessageContaining("negative");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception for invalid balance format")
        void shouldThrowExceptionForInvalidBalanceFormat() {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountId(123L)
                    .initialBalance("invalid")
                    .build();

            when(accountRepository.existsById(123L)).thenReturn(false);

            assertThatThrownBy(() -> accountService.createAccount(request))
                    .isInstanceOf(InvalidTransferException.class)
                    .hasMessageContaining("Invalid amount format");

            verify(accountRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Account Tests")
    class GetAccountTests {

        private Account testAccount;

        @BeforeEach
        void setUp() {
            testAccount = Account.builder()
                    .accountId(123L)
                    .balance(new BigDecimal("100.50"))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .version(0L)
                    .build();
        }

        @Test
        @DisplayName("Should return account details for existing account")
        void shouldReturnAccountDetails() {
            when(accountRepository.findById(123L)).thenReturn(Optional.of(testAccount));

            AccountResponse response = accountService.getAccount(123L);

            assertThat(response.getAccountId()).isEqualTo(123L);
            assertThat(response.getBalance()).isEqualTo("100.5");
        }

        @Test
        @DisplayName("Should throw exception when account not found")
        void shouldThrowExceptionWhenAccountNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccount(999L))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }
}
