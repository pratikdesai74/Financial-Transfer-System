package com.pratikdesai.transfers.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pratikdesai.transfers.dto.request.TransferRequest;
import com.pratikdesai.transfers.exception.*;
import com.pratikdesai.transfers.service.TransferService;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransactionController transactionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(transactionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("POST /transactions - Create Transaction")
    class CreateTransactionTests {

        @Test
        @DisplayName("Should process transfer and return 201 Created")
        void shouldProcessTransfer() throws Exception {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(456L)
                    .amount("100.12345")
                    .build();

            doNothing().when(transferService).transfer(any(TransferRequest.class));

            mockMvc.perform(post("/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(transferService).transfer(any(TransferRequest.class));
        }

        @Test
        @DisplayName("Should return 400 Bad Request for insufficient balance")
        void shouldReturn400ForInsufficientBalance() throws Exception {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(456L)
                    .amount("1000.00")
                    .build();

            doThrow(new InsufficientBalanceException(123L, new BigDecimal("100"), new BigDecimal("1000")))
                    .when(transferService).transfer(any(TransferRequest.class));

            mockMvc.perform(post("/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"));
        }

        @Test
        @DisplayName("Should return 404 Not Found when source account doesn't exist")
        void shouldReturn404WhenSourceAccountNotFound() throws Exception {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(999L)
                    .destinationAccountId(456L)
                    .amount("100.00")
                    .build();

            doThrow(new AccountNotFoundException(999L))
                    .when(transferService).transfer(any(TransferRequest.class));

            mockMvc.perform(post("/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request for same source and destination")
        void shouldReturn400ForSameAccounts() throws Exception {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(123L)
                    .destinationAccountId(123L)
                    .amount("100.00")
                    .build();

            doThrow(new InvalidTransferException("Source and destination accounts must be different"))
                    .when(transferService).transfer(any(TransferRequest.class));

            mockMvc.perform(post("/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_TRANSFER"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request for missing required fields")
        void shouldReturn400ForMissingFields() throws Exception {
            String request = "{\"source_account_id\": 123}";

            mockMvc.perform(post("/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request for invalid amount format")
        void shouldReturn400ForInvalidAmountFormat() throws Exception {
            String request = "{\"source_account_id\": 123, \"destination_account_id\": 456, \"amount\": \"invalid\"}";

            mockMvc.perform(post("/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request for negative account IDs")
        void shouldReturn400ForNegativeAccountIds() throws Exception {
            String request = "{\"source_account_id\": -1, \"destination_account_id\": 456, \"amount\": \"100.00\"}";

            mockMvc.perform(post("/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request for invalid JSON")
        void shouldReturn400ForInvalidJson() throws Exception {
            mockMvc.perform(post("/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("invalid json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_JSON"));
        }
    }
}
