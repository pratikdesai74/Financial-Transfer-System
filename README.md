# Internal Transfers System

A RESTful API service for managing internal financial transfers between accounts. Built with Java Spring Boot and PostgreSQL.

## Features

- Create accounts with initial balances
- Query account balances
- Transfer funds between accounts with full ACID compliance
- Transaction logging for audit trails
- Comprehensive error handling
- High-precision decimal support for financial calculations

## Tech Stack

- **Java 17**
- **Spring Boot 3.2**
- **PostgreSQL 16**
- **Flyway** - Database migrations
- **JPA/Hibernate** - ORM
- **Docker & Docker Compose** - Containerization
- **JUnit 5** - Testing
- **Testcontainers** - Integration testing

## Prerequisites

- Java 17 or higher
- PostgreSQL 14+ (via Docker OR local installation)
- Make (optional, for convenience commands)

## Quick Start

### Option 1: Using Docker Compose (Recommended)

The easiest way to run the application:

```bash
# Start all services (PostgreSQL + Application)
docker-compose up -d --build

# View logs
docker-compose logs -f app

# Stop services
docker-compose down
```

The API will be available at `http://localhost:8080`

### Option 2: Local PostgreSQL Setup (Without Docker)

If you prefer running PostgreSQL locally without Docker:

#### macOS (using Homebrew)

```bash
# Install PostgreSQL
brew install postgresql@16

# Start PostgreSQL service
brew services start postgresql@16

# Create the database user and database
psql -d postgres -c "CREATE USER postgres WITH PASSWORD 'postgres' SUPERUSER;"
psql -d postgres -c "CREATE DATABASE transfers OWNER postgres;"

# Verify setup
PGPASSWORD=postgres psql -h localhost -U postgres -d transfers -c "SELECT 'Connection successful!' as status;"
```

#### Ubuntu/Debian

```bash
# Install PostgreSQL
sudo apt update
sudo apt install postgresql postgresql-contrib

# Start PostgreSQL service
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Create the database user and database
sudo -u postgres psql -c "CREATE USER postgres WITH PASSWORD 'postgres' SUPERUSER;"
sudo -u postgres psql -c "CREATE DATABASE transfers OWNER postgres;"
```

#### Windows

1. Download and install PostgreSQL from https://www.postgresql.org/download/windows/
2. During installation, set password for `postgres` user as `postgres`
3. Open pgAdmin or psql and create database: `CREATE DATABASE transfers;`

#### Run the Application

After setting up PostgreSQL locally:

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or run from IDE
# Run InternalTransfersApplication.main()
```

### Option 3: Local Development with Docker PostgreSQL

Use Docker only for PostgreSQL, run the app locally:

```bash
# Start only PostgreSQL container
docker-compose up -d postgres

# Run the application locally
./mvnw spring-boot:run
```

### Database Connection Details

| Setting | Value |
|---------|-------|
| Host | `localhost` |
| Port | `5432` |
| Database | `transfers` |
| Username | `postgres` |
| Password | `postgres` |

Use these credentials for database tools like DBeaver, pgAdmin, or DataGrip.

### Using Make Commands

```bash
make help              # Show all available commands
make docker-up-build   # Build and start all services
make test              # Run tests
make docker-logs       # View application logs
```

## API Documentation

The full OpenAPI specification is available at [`src/main/resources/openapi.yaml`](src/main/resources/openapi.yaml).

### Create Account

Creates a new account with the specified ID and initial balance.

**Endpoint:** `POST /accounts`

**Request:**
```json
{
  "account_id": 123,
  "initial_balance": "100.23344"
}
```

**Response:**
- `201 Created` - Account created successfully (empty body)
- `409 Conflict` - Account already exists
- `400 Bad Request` - Invalid input data

### Get Account

Retrieves the account details and current balance.

**Endpoint:** `GET /accounts/{account_id}`

**Response:**
```json
{
  "account_id": 123,
  "balance": "100.23344"
}
```

**Status Codes:**
- `200 OK` - Success
- `404 Not Found` - Account not found

### Create Transaction (Transfer)

Transfers funds from one account to another.

**Endpoint:** `POST /transactions`

**Request:**
```json
{
  "source_account_id": 123,
  "destination_account_id": 456,
  "amount": "100.12345"
}
```

**Response:**
- `201 Created` - Transfer completed successfully (empty body)
- `400 Bad Request` - Invalid input, insufficient balance, or same source/destination
- `404 Not Found` - Source or destination account not found

## Error Response Format

All errors return a consistent JSON structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message",
  "details": ["Optional array of validation errors"],
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `ACCOUNT_NOT_FOUND` | 404 | Account does not exist |
| `ACCOUNT_ALREADY_EXISTS` | 409 | Account with this ID already exists |
| `INSUFFICIENT_BALANCE` | 400 | Not enough funds for transfer |
| `INVALID_TRANSFER` | 400 | Invalid transfer parameters |
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `INVALID_JSON` | 400 | Malformed JSON in request |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## Configuration

The application can be configured via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_NAME` | transfers | Database name |
| `DB_USERNAME` | postgres | Database username |
| `DB_PASSWORD` | postgres | Database password |
| `SERVER_PORT` | 8080 | Application port |

## Testing

```bash
# Run all tests
./mvnw test

# Run tests with coverage report
./mvnw test jacoco:report
# Coverage report: target/site/jacoco/index.html
```

## Project Structure

```
src/
├── main/
│   ├── java/com/pratikdesai/transfers/
│   │   ├── InternalTransfersApplication.java
│   │   ├── controller/         # REST endpoints
│   │   ├── dto/                # Request/Response objects
│   │   │   ├── request/
│   │   │   └── response/
│   │   ├── entity/             # JPA entities
│   │   ├── exception/          # Custom exceptions & handlers
│   │   ├── repository/         # Data access layer
│   │   └── service/            # Business logic
│   └── resources/
│       ├── application.yml
│       └── db/migration/       # Flyway migrations
└── test/
    └── java/com/pratikdesai/transfers/
        ├── controller/         # Controller unit tests
        ├── service/            # Service unit tests
        └── integration/        # Integration tests
```

## Assumptions

1. **Single Currency:** All accounts operate in the same currency; no currency conversion is required.

2. **Account IDs:** Account IDs are provided by the client as positive integers and must be unique.

3. **Balance Precision:** Amounts support up to 8 decimal places for high-precision financial calculations.

4. **No Authentication:** Authentication and authorization are not implemented as per requirements.

5. **Atomicity:** All transfers are atomic - either both accounts are updated or neither is (using database transactions with pessimistic locking).

6. **Non-negative Balances:** Account balances cannot go negative; transfers that would result in negative balance are rejected.

7. **Self-transfers:** Transfers to the same account are not allowed.

8. **Concurrent Access:** The system handles concurrent transfers using pessimistic locking with consistent lock ordering to prevent deadlocks.

## Design Decisions

### Pessimistic Locking
Chose pessimistic locking over optimistic locking for transfers because:
- Financial transactions require strong consistency guarantees
- Prevents dirty reads and lost updates
- Lock ordering (by account ID) prevents deadlocks

### Decimal Precision
Using `BigDecimal` with `NUMERIC(19, 8)` in PostgreSQL to:
- Avoid floating-point precision errors
- Support high-precision financial calculations
- Store amounts as strings in JSON to prevent JavaScript precision loss

### Layered Architecture
Following clean architecture principles:
- **Controller layer:** HTTP handling, request validation
- **Service layer:** Business logic, transaction management
- **Repository layer:** Data access abstraction
- **Entity layer:** Domain model

## Health Check

The application exposes health endpoints via Spring Actuator:

```bash
# Health check
curl http://localhost:8080/actuator/health

# Available endpoints
curl http://localhost:8080/actuator
```

## Further Reading

For a comprehensive technical deep-dive covering:
- Docker and container orchestration explained step-by-step
- Database setup and credentials management
- Flyway migrations: how they work, when they run, version tracking
- Database locking: types, when to use which, deadlock prevention
- @Transactional annotation deep-dive
- Testing concurrency and race conditions

See [`docs/TECHNICAL_DEEP_DIVE.md`](docs/TECHNICAL_DEEP_DIVE.md)

## Concurrency Testing

To test the concurrent transfer handling:

```bash
# Using the provided script (requires running application)
./scripts/test_concurrency.sh

# Or run the JUnit concurrency tests
./mvnw test -Dtest=ConcurrencyTest
```

## License

MIT License
