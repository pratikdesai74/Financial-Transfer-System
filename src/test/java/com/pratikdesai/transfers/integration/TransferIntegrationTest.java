package com.pratikdesai.transfers.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pratikdesai.transfers.dto.request.CreateAccountRequest;
import com.pratikdesai.transfers.dto.request.TransferRequest;
import com.pratikdesai.transfers.entity.Account;
import com.pratikdesai.transfers.repository.AccountRepository;
import com.pratikdesai.transfers.repository.TransactionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransferIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Nested
    @DisplayName("Account Creation Integration Tests")
    class AccountCreationTests {

        @Test
        @DisplayName("Should create account and retrieve it")
        void shouldCreateAndRetrieveAccount() throws Exception {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountId(123L)
                    .initialBalance("100.23344")
                    .build();

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/accounts/123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.account_id").value(123))
                    .andExpect(jsonPath("$.balance").value("100.23344"));

            Account account = accountRepository.findById(123L).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("100.23344");
        }

        @Test
        @DisplayName("Should not create duplicate account")
        void shouldNotCreateDuplicateAccount() throws Exception {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountId(123L)
                    .initialBalance("100.00")
                    .build();

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ACCOUNT_ALREADY_EXISTS"));
        }
    }

    @Nested
    @DisplayName("Transfer Integration Tests")
    class TransferTests {

        @Test
        @DisplayName("Should complete transfer between accounts")
        void shouldCompleteTransfer() throws Exception {
            // Create source account
            CreateAccountRequest sourceRequest = CreateAccountRequest.builder()
                    .accountId(123L)
                    .initialBalance("500.00")
                    .build();

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sourceRequest)))
                    .andExpect(status().isCreated());

            // Create destination account
            CreateAccountRequest destRequest = CreateAccountRequest.builder()
                    .accountId(456L)
                    .initialBalance("100.00")
                    .build();

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(destRequest)))
                    .andExpect(status().isCreated());

            // Perform transfer
            TransferRequest transferRequest = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(456L)
                    .amount("100.12345")
                    .build();

            mockMvc.perform(post("/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferRequest)))
                    .andExpect(status().isCreated());

            // Verify source account balance
            mockMvc.perform(get("/accounts/123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value("399.87655"));

            // Verify destination account balance
            mockMvc.perform(get("/accounts/456"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value("200.12345"));

            // Verify transaction was recorded
            assertThat(transactionRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should fail transfer with insufficient balance")
        void shouldFailTransferWithInsufficientBalance() throws Exception {
            // Create source account with small balance
            CreateAccountRequest sourceRequest = CreateAccountRequest.builder()
                    .accountId(123L)
                    .initialBalance("50.00")
                    .build();

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sourceRequest)))
                    .andExpect(status().isCreated());

            // Create destination account
            CreateAccountRequest destRequest = CreateAccountRequest.builder()
                    .accountId(456L)
                    .initialBalance("100.00")
                    .build();

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(destRequest)))
                    .andExpect(status().isCreated());

            // Attempt transfer more than available
            TransferRequest transferRequest = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(456L)
                    .amount("100.00")
                    .build();

            mockMvc.perform(post("/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(transferRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"));

            // Verify balances unchanged
            mockMvc.perform(get("/accounts/123"))
                    .andExpect(jsonPath("$.balance").value("50"));

            mockMvc.perform(get("/accounts/456"))
                    .andExpect(jsonPath("$.balance").value("100"));

            // Verify no transaction was recorded
            assertThat(transactionRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should handle multiple transfers correctly")
        void shouldHandleMultipleTransfers() throws Exception {
            // Create accounts
            mockMvc.perform(post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            CreateAccountRequest.builder().accountId(1L).initialBalance("1000.00").build())));

            mockMvc.perform(post("/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            CreateAccountRequest.builder().accountId(2L).initialBalance("500.00").build())));

            // Transfer 1 -> 2
            mockMvc.perform(post("/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            TransferRequest.builder().sourceAccountId(1L).destinationAccountId(2L).amount("200.00").build())));

            // Transfer 2 -> 1
            mockMvc.perform(post("/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            TransferRequest.builder().sourceAccountId(2L).destinationAccountId(1L).amount("100.00").build())));

            // Transfer 1 -> 2 again
            mockMvc.perform(post("/transactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            TransferRequest.builder().sourceAccountId(1L).destinationAccountId(2L).amount("50.00").build())));

            // Verify final balances
            // Account 1: 1000 - 200 + 100 - 50 = 850
            // Account 2: 500 + 200 - 100 + 50 = 650
            Account account1 = accountRepository.findById(1L).orElseThrow();
            Account account2 = accountRepository.findById(2L).orElseThrow();

            assertThat(account1.getBalance()).isEqualByComparingTo("850.00");
            assertThat(account2.getBalance()).isEqualByComparingTo("650.00");

            // Verify all transactions were recorded
            assertThat(transactionRepository.count()).isEqualTo(3);
        }
    }
}
