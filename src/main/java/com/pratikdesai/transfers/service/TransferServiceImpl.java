package com.pratikdesai.transfers.service;

import com.pratikdesai.transfers.dto.request.TransferRequest;
import com.pratikdesai.transfers.entity.Account;
import com.pratikdesai.transfers.entity.Transaction;
import com.pratikdesai.transfers.entity.TransactionStatus;
import com.pratikdesai.transfers.exception.AccountNotFoundException;
import com.pratikdesai.transfers.exception.InsufficientBalanceException;
import com.pratikdesai.transfers.exception.InvalidTransferException;
import com.pratikdesai.transfers.repository.AccountRepository;
import com.pratikdesai.transfers.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void transfer(TransferRequest request) {
        log.info("Processing transfer from account {} to account {} for amount {}",
                request.getSourceAccountId(), request.getDestinationAccountId(), request.getAmount());

        validateTransferRequest(request);

        BigDecimal amount = parseAmount(request.getAmount());
        validatePositiveAmount(amount);

        // Acquire locks in consistent order to prevent deadlocks
        Long firstAccountId = Math.min(request.getSourceAccountId(), request.getDestinationAccountId());
        Long secondAccountId = Math.max(request.getSourceAccountId(), request.getDestinationAccountId());

        Account firstAccount = accountRepository.findByIdWithLock(firstAccountId)
                .orElseThrow(() -> new AccountNotFoundException(firstAccountId));
        Account secondAccount = accountRepository.findByIdWithLock(secondAccountId)
                .orElseThrow(() -> new AccountNotFoundException(secondAccountId));

        // Identify source and destination based on request
        Account sourceAccount = firstAccountId.equals(request.getSourceAccountId()) ? firstAccount : secondAccount;
        Account destinationAccount = firstAccountId.equals(request.getDestinationAccountId()) ? firstAccount : secondAccount;

        // Validate sufficient balance
        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    sourceAccount.getAccountId(),
                    sourceAccount.getBalance(),
                    amount
            );
        }

        // Perform the transfer
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));

        // Save updated accounts
        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        // Log the transaction
        Transaction transaction = Transaction.builder()
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationAccountId())
                .amount(amount)
                .status(TransactionStatus.COMPLETED)
                .build();

        transactionRepository.save(transaction);

        log.info("Transfer completed successfully. Transaction ID: {}", transaction.getTransactionId());
    }

    private void validateTransferRequest(TransferRequest request) {
        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new InvalidTransferException("Source and destination accounts must be different");
        }
    }

    private BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw new InvalidTransferException("Invalid amount format: " + amount);
        }
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be greater than zero");
        }
    }
}
