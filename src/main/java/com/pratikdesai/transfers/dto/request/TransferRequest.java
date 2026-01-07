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
public class TransferRequest {

    @NotNull(message = "Source account ID is required")
    @Positive(message = "Source account ID must be a positive number")
    @JsonProperty("source_account_id")
    private Long sourceAccountId;

    @NotNull(message = "Destination account ID is required")
    @Positive(message = "Destination account ID must be a positive number")
    @JsonProperty("destination_account_id")
    private Long destinationAccountId;

    @NotNull(message = "Amount is required")
    @Pattern(regexp = "^\\d+(\\.\\d+)?$", message = "Amount must be a valid positive decimal number")
    @JsonProperty("amount")
    private String amount;
}
