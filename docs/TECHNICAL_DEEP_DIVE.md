# Building a Production-Ready Financial Transfer System with Spring Boot

A comprehensive guide to building a robust, concurrent-safe financial transfer system using Spring Boot, PostgreSQL, Docker, and Flyway.

## Table of Contents
1. [System Overview](#system-overview)
2. [Docker: How It All Works Together](#docker-how-it-all-works-together)
3. [Database Setup & Credentials Management](#database-setup--credentials-management)
4. [Flyway: Database Migrations Explained](#flyway-database-migrations-explained)
5. [Understanding Database Locking](#understanding-database-locking)
6. [The @Transactional Annotation Deep Dive](#the-transactional-annotation-deep-dive)
7. [Testing Concurrency & Race Conditions](#testing-concurrency--race-conditions)
8. [Common Interview Questions](#common-interview-questions)

---

## System Overview

This system provides three core APIs:
- **POST /accounts** - Create an account with initial balance
- **GET /accounts/{id}** - Query account balance
- **POST /transactions** - Transfer funds between accounts

The challenge? Ensuring that concurrent transfers don't corrupt data (no lost money!).

---

## Docker: How It All Works Together

### What is Docker?

Think of Docker as a **shipping container for software**. Just like shipping containers standardized global trade (any container fits on any ship), Docker containers ensure your application runs the same everywhere.

### Our Docker Setup Explained

#### 1. The Dockerfile (Building the Application Container)

```dockerfile
# Build stage - Like a construction site
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B    # Download all dependencies
COPY src ./src
RUN mvn package -DskipTests -B      # Build the JAR file

# Runtime stage - The finished product
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why two stages (Multi-stage build)?**
- **Build stage**: Contains Maven, source code, all build tools (~800MB)
- **Runtime stage**: Only contains JRE and the JAR file (~200MB)
- Result: Smaller, faster, more secure container

#### 2. The docker-compose.yml (Orchestrating Multiple Containers)

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: transfers
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d transfers"]
      interval: 10s

  app:
    build: .
    depends_on:
      postgres:
        condition: service_healthy   # Wait for DB to be ready!
    environment:
      DB_HOST: postgres              # Container name becomes hostname
      DB_PORT: 5432
    ports:
      - "8080:8080"
```

### Step-by-Step: What Happens When You Run `docker-compose up`

```
┌─────────────────────────────────────────────────────────────────┐
│                    docker-compose up -d                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 1: Create Network                                          │
│ Docker creates a private network: "transfers-network"           │
│ Containers can communicate using service names as hostnames     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 2: Start PostgreSQL Container                              │
│ - Pulls postgres:16-alpine image (if not cached)                │
│ - Creates container "transfers-db"                              │
│ - Initializes database with POSTGRES_DB, USER, PASSWORD         │
│ - Runs healthcheck every 10 seconds                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 3: Wait for PostgreSQL to be Healthy                       │
│ Docker runs: pg_isready -U postgres -d transfers                │
│ Waits until this command succeeds (DB accepting connections)    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 4: Build Application Image                                 │
│ - Executes Dockerfile                                           │
│ - Downloads Maven dependencies                                  │
│ - Compiles Java code                                            │
│ - Creates JAR file                                              │
│ - Creates final lightweight image                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Step 5: Start Application Container                             │
│ - Injects environment variables (DB_HOST=postgres, etc.)        │
│ - Starts Spring Boot application                                │
│ - App connects to "postgres:5432" (resolved via Docker DNS)     │
│ - Flyway runs migrations                                        │
│ - App ready on port 8080                                        │
└─────────────────────────────────────────────────────────────────┘
```

### Key Docker Concepts

| Concept | Explanation |
|---------|-------------|
| **Image** | A blueprint/template (like a Java class) |
| **Container** | A running instance (like a Java object) |
| **Volume** | Persistent storage that survives container restarts |
| **Network** | Virtual network allowing container-to-container communication |
| **Port Mapping** | `8080:8080` means host port 8080 → container port 8080 |

---

## Database Setup & Credentials Management

### How Credentials Flow

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ docker-compose   │────▶│ Spring Boot App  │────▶│   PostgreSQL     │
│    .yml          │     │                  │     │                  │
│                  │     │                  │     │                  │
│ DB_HOST=postgres │     │ Reads env vars   │     │ Authenticates    │
│ DB_USER=postgres │     │ via ${DB_HOST}   │     │ connection       │
│ DB_PASSWORD=xxx  │     │                  │     │                  │
└──────────────────┘     └──────────────────┘     └──────────────────┘
```

### application.yml Configuration

```yaml
spring:
  datasource:
    # ${VAR:default} syntax - use env var, or default if not set
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:transfers}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

**How it works:**
1. Spring reads `${DB_HOST:localhost}`
2. Checks for environment variable `DB_HOST`
3. If found → uses that value (`postgres` from docker-compose)
4. If not found → uses default (`localhost` for local development)

### Production Best Practices

```yaml
# DON'T do this in production:
password: mysecretpassword

# DO this instead:
password: ${DB_PASSWORD}  # Injected from environment/secrets manager
```

**Secret Management Options:**
- Docker Secrets
- Kubernetes Secrets
- AWS Secrets Manager / Azure Key Vault
- HashiCorp Vault

---

## Flyway: Database Migrations Explained

### What Problem Does Flyway Solve?

Imagine this scenario:
- Developer A adds a `phone` column to the `users` table
- Developer B doesn't have this column locally
- Production database doesn't have it either
- **Chaos ensues!**

Flyway ensures **database schema is versioned** just like your code.

### How Flyway Works

```
┌─────────────────────────────────────────────────────────────────┐
│                   Application Startup                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Flyway Checks: Does flyway_schema_history table exist?          │
│                                                                 │
│ NO  ──────────▶ Creates the table                               │
│ YES ──────────▶ Continues                                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Flyway Scans: src/main/resources/db/migration/                  │
│                                                                 │
│ Found files:                                                    │
│   V1__create_initial_schema.sql                                 │
│   V2__add_audit_columns.sql (if exists)                         │
│   V3__add_indexes.sql (if exists)                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Flyway Compares: Which migrations are NOT in history table?     │
│                                                                 │
│ History table has: V1                                           │
│ Files found: V1, V2, V3                                         │
│ Pending: V2, V3                                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ Flyway Executes: Pending migrations in order                    │
│                                                                 │
│ 1. Execute V2__add_audit_columns.sql                            │
│ 2. Record in flyway_schema_history                              │
│ 3. Execute V3__add_indexes.sql                                  │
│ 4. Record in flyway_schema_history                              │
└─────────────────────────────────────────────────────────────────┘
```

### The flyway_schema_history Table

```sql
-- Flyway automatically creates and manages this table
SELECT * FROM flyway_schema_history;
```

| installed_rank | version | description | type | script | checksum | installed_by | installed_on | execution_time | success |
|---------------|---------|-------------|------|--------|----------|--------------|--------------|----------------|---------|
| 1 | 1 | create initial schema | SQL | V1__create_initial_schema.sql | -157832 | postgres | 2024-01-15 10:30:00 | 45 | true |

**Key columns:**
- **version**: The version number from filename
- **checksum**: Hash of the file content (detects tampering!)
- **success**: Whether migration succeeded
- **installed_on**: When it was applied

### Migration File Naming Convention

```
V1__create_initial_schema.sql
│ │  │
│ │  └── Description (underscores become spaces in logs)
│ └───── Double underscore separator (required!)
└─────── V = Versioned, number = version
```

### Our Migration File

```sql
-- V1__create_initial_schema.sql

-- Create accounts table
CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY,
    balance NUMERIC(19, 8) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,  -- For optimistic locking
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

-- Create transactions table (audit log)
CREATE TABLE transactions (
    transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    destination_account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    amount NUMERIC(19, 8) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_different_accounts CHECK (source_account_id != destination_account_id)
);

-- Indexes for performance
CREATE INDEX idx_transactions_source ON transactions(source_account_id);
CREATE INDEX idx_transactions_destination ON transactions(destination_account_id);
```

### Verify Migrations in Database

```bash
# Connect to PostgreSQL container
docker exec -it transfers-db psql -U postgres -d transfers

# Check Flyway history
SELECT version, description, success, installed_on
FROM flyway_schema_history;

# Check tables were created
\dt
```

---

## Understanding Database Locking

### Why Do We Need Locking?

Consider two concurrent transfers:
- Transfer A: Account 1 → Account 2, $100
- Transfer B: Account 1 → Account 3, $100

Account 1 has $150. What happens without locking?

```
┌─────────────────────────────────────────────────────────────────┐
│                    WITHOUT PROPER LOCKING                       │
│                    (Race Condition Bug!)                        │
└─────────────────────────────────────────────────────────────────┘

Time    Thread A (Transfer to Acc 2)    Thread B (Transfer to Acc 3)
─────   ──────────────────────────────  ──────────────────────────────
T1      Read balance: $150
T2                                      Read balance: $150
T3      Check: 150 >= 100? ✓
T4                                      Check: 150 >= 100? ✓
T5      New balance: 150-100 = $50
T6                                      New balance: 150-100 = $50
T7      Write balance: $50
T8                                      Write balance: $50

RESULT: Account 1 has $50, but $200 was transferred!
        We just created $50 out of thin air! 💸
```

### Types of Database Locking

#### 1. Optimistic Locking (Version-based)

**Concept:** "I hope nobody else modified this data"

```java
@Entity
public class Account {
    @Version  // Magic annotation!
    private Long version;
}
```

**How it works:**
```sql
-- Read account (version = 1)
SELECT * FROM accounts WHERE account_id = 1;

-- Update with version check
UPDATE accounts
SET balance = 50, version = 2
WHERE account_id = 1 AND version = 1;

-- If version changed, 0 rows updated → Exception thrown!
```

**Pros:**
- No database locks held
- Better for read-heavy workloads
- Higher throughput

**Cons:**
- Fails under high contention
- Requires retry logic

#### 2. Pessimistic Locking (Row-level locks)

**Concept:** "I'm locking this data NOW, nobody else can touch it"

```java
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)  // SELECT ... FOR UPDATE
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findByIdWithLock(@Param("accountId") Long accountId);
}
```

**How it works:**
```sql
-- Thread A acquires lock
SELECT * FROM accounts WHERE account_id = 1 FOR UPDATE;
-- Row is now LOCKED

-- Thread B tries to read same row
SELECT * FROM accounts WHERE account_id = 1 FOR UPDATE;
-- Thread B WAITS until Thread A releases lock

-- Thread A commits transaction
COMMIT;
-- Lock released, Thread B continues
```

**Pros:**
- Guarantees consistency
- No retry logic needed
- Works under high contention

**Cons:**
- Can cause deadlocks
- Lower throughput
- Holds locks longer

### Which Locking to Choose?

```
┌─────────────────────────────────────────────────────────────────┐
│                    DECISION FLOWCHART                           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ Is data modification critical │
              │ (financial, inventory, etc.)? │
              └───────────────────────────────┘
                      │               │
                     YES              NO
                      │               │
                      ▼               ▼
         ┌────────────────┐   ┌────────────────┐
         │ High conflict  │   │ Use Optimistic │
         │ probability?   │   │    Locking     │
         └────────────────┘   └────────────────┘
              │        │
             YES       NO
              │        │
              ▼        ▼
    ┌──────────────┐ ┌──────────────┐
    │ PESSIMISTIC  │ │  OPTIMISTIC  │
    │   LOCKING    │ │   LOCKING    │
    │              │ │ (with retry) │
    └──────────────┘ └──────────────┘
```

**Our Choice: Pessimistic Locking**

For financial transfers, we chose pessimistic locking because:
1. **Money is critical** - Can't afford lost updates
2. **High contention likely** - Popular accounts get many transfers
3. **Simpler code** - No retry logic needed
4. **Guaranteed success** - Once you acquire lock, you will complete

### Preventing Deadlocks

**The Problem:**
```
Thread A: Lock Account 1, then Lock Account 2
Thread B: Lock Account 2, then Lock Account 1

Thread A holds 1, waits for 2
Thread B holds 2, waits for 1
DEADLOCK! Both wait forever.
```

**The Solution: Consistent Lock Ordering**

```java
public void transfer(Long sourceId, Long destinationId, BigDecimal amount) {
    // ALWAYS lock lower ID first!
    Long firstId = Math.min(sourceId, destinationId);
    Long secondId = Math.max(sourceId, destinationId);

    Account first = accountRepository.findByIdWithLock(firstId);
    Account second = accountRepository.findByIdWithLock(secondId);

    // Now safe to proceed...
}
```

```
Thread A: Transfer 1→2, locks 1 first, then 2
Thread B: Transfer 2→1, locks 1 first, then 2

Both try to lock 1 first → One waits → No deadlock!
```

---

## The @Transactional Annotation Deep Dive

### What is a Transaction?

A transaction is a sequence of operations that are treated as a **single unit of work**.

**ACID Properties:**
- **Atomicity**: All or nothing (either all succeed or all rollback)
- **Consistency**: Database goes from one valid state to another
- **Isolation**: Concurrent transactions don't interfere
- **Durability**: Once committed, data is permanent

### How @Transactional Works

```java
@Service
public class TransferServiceImpl {

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void transfer(TransferRequest request) {
        // Everything inside this method is ONE transaction

        Account source = accountRepository.findByIdWithLock(sourceId);
        Account dest = accountRepository.findByIdWithLock(destId);

        source.setBalance(source.getBalance().subtract(amount));
        dest.setBalance(dest.getBalance().add(amount));

        accountRepository.save(source);
        accountRepository.save(dest);
        transactionRepository.save(transactionLog);

        // If ANY exception occurs, ALL changes are rolled back!
    }
}
```

### Behind the Scenes

```
┌─────────────────────────────────────────────────────────────────┐
│                 @Transactional Proxy Magic                      │
└─────────────────────────────────────────────────────────────────┘

Your code calls:  transferService.transfer(request)
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Proxy                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 1. BEGIN TRANSACTION                                      │  │
│  │ 2. Call actual transfer() method                          │  │
│  │ 3. If success → COMMIT                                    │  │
│  │    If exception → ROLLBACK                                │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    Actual TransferServiceImpl
```

### Isolation Levels Explained

| Level | Dirty Read | Non-Repeatable Read | Phantom Read |
|-------|------------|---------------------|--------------|
| READ_UNCOMMITTED | Possible | Possible | Possible |
| READ_COMMITTED | Prevented | Possible | Possible |
| REPEATABLE_READ | Prevented | Prevented | Possible |
| SERIALIZABLE | Prevented | Prevented | Prevented |

**Our choice: REPEATABLE_READ**
- Ensures we see consistent data throughout transaction
- Prevents another transaction from modifying rows we've read
- Good balance of consistency vs performance

### Common @Transactional Mistakes

```java
// MISTAKE 1: Calling from same class (proxy bypassed!)
@Service
public class AccountService {

    public void doSomething() {
        transfer();  // ❌ @Transactional ignored!
    }

    @Transactional
    public void transfer() { ... }
}

// MISTAKE 2: Catching exception (prevents rollback!)
@Transactional
public void transfer() {
    try {
        // risky code
    } catch (Exception e) {
        log.error("Error", e);  // ❌ Transaction commits anyway!
    }
}

// CORRECT: Let exception propagate
@Transactional
public void transfer() {
    // If exception occurs, transaction rolls back automatically
}
```

---

## Testing Concurrency & Race Conditions

### Unit Test for Concurrent Transfers

```java
@Test
@DisplayName("Should handle concurrent transfers without data corruption")
void shouldHandleConcurrentTransfers() throws Exception {
    // Setup: Account with $1000
    accountService.createAccount(new CreateAccountRequest(1L, "1000"));
    accountService.createAccount(new CreateAccountRequest(2L, "0"));

    // Execute: 10 concurrent transfers of $100 each
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                transferService.transfer(new TransferRequest(1L, 2L, "100"));
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await(10, TimeUnit.SECONDS);

    // Verify: Total money unchanged, balances correct
    Account acc1 = accountRepository.findById(1L).orElseThrow();
    Account acc2 = accountRepository.findById(2L).orElseThrow();

    // $1000 total should remain constant
    assertThat(acc1.getBalance().add(acc2.getBalance()))
        .isEqualByComparingTo("1000");
}
```

### Testing Race Conditions Manually

```bash
# Terminal 1: Start transfer that will be slow
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{"source_account_id": 1, "destination_account_id": 2, "amount": "500"}'

# Terminal 2: Immediately start another transfer
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{"source_account_id": 1, "destination_account_id": 3, "amount": "500"}'

# Check results - one should fail with insufficient balance
curl http://localhost:8080/accounts/1
```

### Load Testing with Apache Bench

```bash
# Create test data
echo '{"source_account_id": 1, "destination_account_id": 2, "amount": "1"}' > transfer.json

# Send 100 concurrent requests
ab -n 100 -c 10 -p transfer.json -T "application/json" \
   http://localhost:8080/transactions

# Verify data integrity
curl http://localhost:8080/accounts/1
curl http://localhost:8080/accounts/2
```

---

## Common Interview Questions

### Q1: "Why did you choose PostgreSQL over MySQL?"

**Answer:** PostgreSQL offers:
- Better support for concurrent transactions (MVCC)
- `NUMERIC` type for exact decimal arithmetic
- `FOR UPDATE` locking is more reliable
- Better default isolation level behavior

### Q2: "What happens if the application crashes mid-transfer?"

**Answer:** The database transaction automatically rolls back:
- All changes are undone
- No partial updates
- Data remains consistent
- This is the "A" in ACID (Atomicity)

### Q3: "How would you scale this system?"

**Answer:** Several options:
1. **Read replicas** for balance queries
2. **Connection pooling** (HikariCP already configured)
3. **Horizontal scaling** with multiple app instances (locking handles concurrency)
4. **Account sharding** for massive scale (partition by account_id range)

### Q4: "Why use BigDecimal instead of double for money?"

**Answer:**
```java
// WRONG - floating point errors
double balance = 0.1 + 0.2;  // = 0.30000000000000004

// CORRECT - exact arithmetic
BigDecimal balance = new BigDecimal("0.1").add(new BigDecimal("0.2"));  // = 0.3
```

### Q5: "How do you prevent SQL injection?"

**Answer:** We use:
1. **JPA/Hibernate** - Parameterized queries by default
2. **Spring Data** - Methods like `findById()` are safe
3. **Validation** - Input validated before reaching database

### Q6: "Explain the flow of a transfer request"

**Answer:**
```
HTTP Request → Controller → Service → Repository → Database
     │              │           │           │           │
     │        Validates    @Transactional  @Lock    FOR UPDATE
     │          input         begins      query      lock row
     │              │           │           │           │
     │              └───────────┴───────────┴───────────┘
     │                          │
     │                   If success: COMMIT
     │                   If error: ROLLBACK
     │                          │
     ◄──────────── HTTP Response (201 or error)
```

---

## Quick Reference Commands

```bash
# Start everything
docker-compose up -d --build

# View logs
docker-compose logs -f app

# Connect to database
docker exec -it transfers-db psql -U postgres -d transfers

# Check Flyway migrations
SELECT * FROM flyway_schema_history;

# Check account balances
SELECT * FROM accounts;

# Check transaction history
SELECT * FROM transactions ORDER BY created_at DESC;

# Stop everything
docker-compose down

# Stop and remove data
docker-compose down -v
```

---

## Conclusion

Building a financial transfer system requires careful attention to:

1. **Data Integrity** - Use proper locking and transactions
2. **Concurrency** - Prevent race conditions with pessimistic locking
3. **Infrastructure** - Docker ensures consistent environments
4. **Database Migrations** - Flyway keeps schema versioned
5. **Testing** - Verify concurrent behavior explicitly

The key insight: **In financial systems, correctness beats performance**. It's better to be slower and correct than fast and wrong.

---

*Feel free to reach out if you have questions about implementing similar systems!*
