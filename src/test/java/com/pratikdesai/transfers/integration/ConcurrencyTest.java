package com.pratikdesai.transfers.integration;

import com.pratikdesai.transfers.dto.request.CreateAccountRequest;
import com.pratikdesai.transfers.dto.request.TransferRequest;
import com.pratikdesai.transfers.entity.Account;
import com.pratikdesai.transfers.repository.AccountRepository;
import com.pratikdesai.transfers.repository.TransactionRepository;
import com.pratikdesai.transfers.service.AccountService;
import com.pratikdesai.transfers.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests to verify that the transfer system handles
 * race conditions correctly using pessimistic locking.
 *
 * These tests demonstrate:
 * 1. Data integrity under concurrent access (MOST IMPORTANT - money is never created/destroyed)
 * 2. No lost updates
 * 3. Proper handling of insufficient balance scenarios
 *
 * NOTE: H2 in-memory database handles locking differently than PostgreSQL.
 * For full concurrency testing, use the test_concurrency.sh script with
 * a real PostgreSQL instance (via docker-compose).
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("Should maintain data integrity - total money never changes under concurrent access")
    void shouldMaintainDataIntegrityWithConcurrentTransfers() throws Exception {
        // Given: Account 1 has $1000, Account 2 has $0
        accountService.createAccount(new CreateAccountRequest(1L, "1000"));
        accountService.createAccount(new CreateAccountRequest(2L, "0"));

        // When: 10 concurrent threads each try to transfer $100
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transferService.transfer(
                            TransferRequest.builder()
                                    .sourceAccountId(1L)
                                    .destinationAccountId(2L)
                                    .amount("100")
                                    .build()
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: THE MOST IMPORTANT ASSERTION - Total money must remain $1000
        Account account1 = accountRepository.findById(1L).orElseThrow();
        Account account2 = accountRepository.findById(2L).orElseThrow();

        BigDecimal totalMoney = account1.getBalance().add(account2.getBalance());
        assertThat(totalMoney)
                .as("Total money should remain constant - no money created or destroyed!")
                .isEqualByComparingTo("1000");

        // The number of successful transfers depends on timing and locking
        // What matters is: successes + failures = threadCount
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);

        // Money transferred should equal 100 * successCount
        BigDecimal transferred = new BigDecimal(successCount.get() * 100);
        assertThat(account2.getBalance()).isEqualByComparingTo(transferred);
        assertThat(account1.getBalance()).isEqualByComparingTo(new BigDecimal("1000").subtract(transferred));

        System.out.println("Data integrity test passed!");
        System.out.println("   Successful transfers: " + successCount.get());
        System.out.println("   Failed transfers: " + failCount.get());
        System.out.println("   Account 1 balance: " + account1.getBalance());
        System.out.println("   Account 2 balance: " + account2.getBalance());
        System.out.println("   Total money preserved: " + totalMoney);
    }

    @Test
    @DisplayName("Should correctly prevent overdraft in concurrent scenario")
    void shouldPreventOverdraftConcurrently() throws Exception {
        // Given: Account 1 has $500, trying to transfer $100 x 10 = $1000 total
        accountService.createAccount(new CreateAccountRequest(1L, "500"));
        accountService.createAccount(new CreateAccountRequest(2L, "0"));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transferService.transfer(
                            TransferRequest.builder()
                                    .sourceAccountId(1L)
                                    .destinationAccountId(2L)
                                    .amount("100")
                                    .build()
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: Verify no overdraft occurred
        Account account1 = accountRepository.findById(1L).orElseThrow();
        Account account2 = accountRepository.findById(2L).orElseThrow();

        // Account 1 should never go negative
        assertThat(account1.getBalance())
                .as("Account should never go negative - overdraft must be prevented!")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // Total money must remain $500
        BigDecimal totalMoney = account1.getBalance().add(account2.getBalance());
        assertThat(totalMoney)
                .as("Total money should remain constant!")
                .isEqualByComparingTo("500");

        // At most 5 transfers can succeed ($500 / $100 = 5)
        assertThat(successCount.get())
                .as("At most 5 transfers should succeed with $500 available")
                .isLessThanOrEqualTo(5);

        System.out.println("Overdraft prevention test passed!");
        System.out.println("   Successful transfers: " + successCount.get());
        System.out.println("   Failed transfers: " + failCount.get());
        System.out.println("   Account 1 balance: " + account1.getBalance());
        System.out.println("   Account 2 balance: " + account2.getBalance());
    }

    @Test
    @DisplayName("Should prevent deadlocks with bidirectional concurrent transfers")
    void shouldPreventDeadlocksWithBidirectionalTransfers() throws Exception {
        // Given: Two accounts with $500 each
        accountService.createAccount(new CreateAccountRequest(1L, "500"));
        accountService.createAccount(new CreateAccountRequest(2L, "500"));

        // When: Concurrent bidirectional transfers (1->2 and 2->1)
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int direction = i % 2;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (direction == 0) {
                        transferService.transfer(
                                TransferRequest.builder()
                                        .sourceAccountId(1L)
                                        .destinationAccountId(2L)
                                        .amount("10")
                                        .build()
                        );
                    } else {
                        transferService.transfer(
                                TransferRequest.builder()
                                        .sourceAccountId(2L)
                                        .destinationAccountId(1L)
                                        .amount("10")
                                        .build()
                        );
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        // THE KEY ASSERTION: No deadlock - test must complete within timeout
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed)
                .as("Test must complete without deadlock!")
                .isTrue();

        Account account1 = accountRepository.findById(1L).orElseThrow();
        Account account2 = accountRepository.findById(2L).orElseThrow();

        // Total money should remain $1000
        BigDecimal totalMoney = account1.getBalance().add(account2.getBalance());
        assertThat(totalMoney)
                .as("Total money should remain constant!")
                .isEqualByComparingTo("1000");

        System.out.println("Deadlock prevention test passed!");
        System.out.println("   Test completed in time (no deadlock)");
        System.out.println("   Successful transfers: " + successCount.get());
        System.out.println("   Failed transfers: " + exceptions.size());
        System.out.println("   Account 1 balance: " + account1.getBalance());
        System.out.println("   Account 2 balance: " + account2.getBalance());
        System.out.println("   Total money preserved: " + totalMoney);
    }

    @Test
    @DisplayName("Should handle high volume transfers without data corruption")
    void shouldHandleHighVolumeTransfersWithoutCorruption() throws Exception {
        // Given: 5 accounts with $1000 each = $5000 total
        for (long i = 1; i <= 5; i++) {
            accountService.createAccount(new CreateAccountRequest(i, "1000"));
        }

        // When: 50 transfers between accounts
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long sourceId = (i % 5) + 1;
            final long destId = ((i + 1) % 5) + 1;
            executor.submit(() -> {
                try {
                    if (sourceId != destId) {
                        transferService.transfer(
                                TransferRequest.builder()
                                        .sourceAccountId(sourceId)
                                        .destinationAccountId(destId)
                                        .amount("10")
                                        .build()
                        );
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    endLatch.countDown();
                }
            });
        }

        endLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: Total money across all accounts must be $5000
        BigDecimal totalMoney = BigDecimal.ZERO;
        System.out.println("Account balances after high-volume test:");
        for (long i = 1; i <= 5; i++) {
            Account account = accountRepository.findById(i).orElseThrow();
            totalMoney = totalMoney.add(account.getBalance());
            System.out.println("   Account " + i + " balance: " + account.getBalance());
        }

        assertThat(totalMoney)
                .as("Total money must be preserved in high-volume scenario!")
                .isEqualByComparingTo("5000");

        System.out.println("High volume test passed!");
        System.out.println("   Successful transfers: " + successCount.get());
        System.out.println("   Total money preserved: " + totalMoney);
    }
}
