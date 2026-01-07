package com.pratikdesai.transfers.exception;

public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException(Long accountId) {
        super("Account already exists with ID: " + accountId);
    }
}
