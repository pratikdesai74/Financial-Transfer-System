package com.pratikdesai.transfers.service;

import com.pratikdesai.transfers.dto.request.CreateAccountRequest;
import com.pratikdesai.transfers.dto.response.AccountResponse;

public interface AccountService {

    void createAccount(CreateAccountRequest request);

    AccountResponse getAccount(Long accountId);
}
