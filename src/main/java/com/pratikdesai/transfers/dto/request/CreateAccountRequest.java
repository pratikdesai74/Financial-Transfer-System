package com.pratikdesai.transfers.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

    @NotNull(message = "Account ID is required")
    @Positive(message = "Account ID must be a positive number")
    @JsonProperty("account_id")
    private Long accountId;

    @NotNull(message = "Initial balance is required")
    @Pattern(regexp = "^-?\\d+(\\.\\d+)?$", message = "Initial balance must be a valid decimal number")
    @JsonProperty("initial_balance")
    private String initialBalance;
}
