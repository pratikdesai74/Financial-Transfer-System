package com.pratikdesai.transfers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pratikdesai.transfers.dto.request.CreateAccountRequest;
import com.pratikdesai.transfers.dto.response.AccountResponse;
import com.pratikdesai.transfers.exception.AccountAlreadyExistsException;
import com.pratikdesai.transfers.exception.AccountNotFoundException;
import com.pratikdesai.transfers.exception.GlobalExceptionHandler;
import com.pratikdesai.transfers.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(accountController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("POST /accounts - Create Account")
    class CreateAccountTests {

        @Test
        @DisplayName("Should create account and return 201 Created")
        void shouldCreateAccount() throws Exception {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountId(123L)
                    .initialBalance("100.23344")
                    .build();

            doNothing().when(accountService).createAccount(any(CreateAccountRequest.class));

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(accountService).createAccount(any(CreateAccountRequest.class));
        }

        @Test
        @DisplayName("Should return 409 Conflict when account already exists")
        void shouldReturn409WhenAccountExists() throws Exception {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountId(123L)
                    .initialBalance("100.23344")
                    .build();

            doThrow(new AccountAlreadyExistsException(123L))
                    .when(accountService).createAccount(any(CreateAccountRequest.class));

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ACCOUNT_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request for missing account_id")
        void shouldReturn400ForMissingAccountId() throws Exception {
            String request = "{\"initial_balance\": \"100.00\"}";

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request for invalid balance format")
        void shouldReturn400ForInvalidBalance() throws Exception {
            String request = "{\"account_id\": 123, \"initial_balance\": \"invalid\"}";

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request for negative account_id")
        void shouldReturn400ForNegativeAccountId() throws Exception {
            String request = "{\"account_id\": -1, \"initial_balance\": \"100.00\"}";

            mockMvc.perform(post("/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("GET /accounts/{accountId} - Get Account")
    class GetAccountTests {

        @Test
        @DisplayName("Should return account details with 200 OK")
        void shouldReturnAccountDetails() throws Exception {
            AccountResponse response = AccountResponse.builder()
                    .accountId(123L)
                    .balance("100.23344")
                    .build();

            when(accountService.getAccount(123L)).thenReturn(response);

            mockMvc.perform(get("/accounts/123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.account_id").value(123))
                    .andExpect(jsonPath("$.balance").value("100.23344"));
        }

        @Test
        @DisplayName("Should return 404 Not Found when account doesn't exist")
        void shouldReturn404WhenAccountNotFound() throws Exception {
            when(accountService.getAccount(999L))
                    .thenThrow(new AccountNotFoundException(999L));

            mockMvc.perform(get("/accounts/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request for invalid account ID format")
        void shouldReturn400ForInvalidAccountIdFormat() throws Exception {
            mockMvc.perform(get("/accounts/invalid"))
                    .andExpect(status().isBadRequest());
        }
    }
}
