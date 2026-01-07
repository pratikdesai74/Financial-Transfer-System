package com.pratikdesai.transfers.controller;

import com.pratikdesai.transfers.dto.request.CreateAccountRequest;
import com.pratikdesai.transfers.dto.response.AccountResponse;
import com.pratikdesai.transfers.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<Void> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        log.info("Received request to create account with ID: {}", request.getAccountId());
        accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long accountId) {
        log.info("Received request to get account with ID: {}", accountId);
        AccountResponse response = accountService.getAccount(accountId);
        return ResponseEntity.ok(response);
    }
}
