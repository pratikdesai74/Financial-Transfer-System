package com.pratikdesai.transfers.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(Long accountId, BigDecimal available, BigDecimal required) {
        super(String.format("Insufficient balance in account %d. Available: %s, Required: %s",
                accountId, available.toPlainString(), required.toPlainString()));
    }
}
