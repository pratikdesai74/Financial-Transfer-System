package com.pratikdesai.transfers.service;

import com.pratikdesai.transfers.dto.request.CreateAccountRequest;
import com.pratikdesai.transfers.dto.response.AccountResponse;
import com.pratikdesai.transfers.entity.Account;
import com.pratikdesai.transfers.exception.AccountAlreadyExistsException;
import com.pratikdesai.transfers.exception.AccountNotFoundException;
import com.pratikdesai.transfers.exception.InvalidTransferException;
import com.pratikdesai.transfers.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public void createAccount(CreateAccountRequest request) {
        log.info("Creating account with ID: {}", request.getAccountId());

        if (accountRepository.existsById(request.getAccountId())) {
            throw new AccountAlreadyExistsException(request.getAccountId());
        }

        BigDecimal initialBalance = parseAmount(request.getInitialBalance());
        validateNonNegativeAmount(initialBalance, "Initial balance");

        Account account = Account.builder()
                .accountId(request.getAccountId())
                .balance(initialBalance)
                .build();

        accountRepository.save(account);
        log.info("Account created successfully with ID: {}", request.getAccountId());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        log.debug("Fetching account with ID: {}", accountId);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .balance(account.getBalance().stripTrailingZeros().toPlainString())
                .build();
    }

    private BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw new InvalidTransferException("Invalid amount format: " + amount);
        }
    }

    private void validateNonNegativeAmount(BigDecimal amount, String fieldName) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidTransferException(fieldName + " cannot be negative");
        }
    }
}
