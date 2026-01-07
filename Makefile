.PHONY: build test run clean docker-build docker-up docker-down help

# Default target
.DEFAULT_GOAL := help

# Variables
MVN := ./mvnw
DOCKER_COMPOSE := docker-compose

## help: Show this help message
help:
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@sed -n 's/^##//p' $(MAKEFILE_LIST) | column -t -s ':' | sed -e 's/^/ /'

## build: Build the application (skip tests)
build:
	$(MVN) clean package -DskipTests

## test: Run all tests
test:
	$(MVN) test

## test-coverage: Run tests with coverage report
test-coverage:
	$(MVN) test jacoco:report
	@echo "Coverage report generated at target/site/jacoco/index.html"

## run: Run the application locally (requires PostgreSQL)
run:
	$(MVN) spring-boot:run

## clean: Clean build artifacts
clean:
	$(MVN) clean

## docker-build: Build Docker image
docker-build:
	docker build -t internal-transfers:latest .

## docker-up: Start all services with Docker Compose
docker-up:
	$(DOCKER_COMPOSE) up -d

## docker-up-build: Build and start all services with Docker Compose
docker-up-build:
	$(DOCKER_COMPOSE) up -d --build

## docker-down: Stop all services
docker-down:
	$(DOCKER_COMPOSE) down

## docker-logs: View application logs
docker-logs:
	$(DOCKER_COMPOSE) logs -f app

## docker-clean: Stop services and remove volumes
docker-clean:
	$(DOCKER_COMPOSE) down -v

## db-start: Start only PostgreSQL container
db-start:
	$(DOCKER_COMPOSE) up -d postgres

## db-stop: Stop PostgreSQL container
db-stop:
	$(DOCKER_COMPOSE) stop postgres

## verify: Run all checks (compile, test, verify)
verify:
	$(MVN) verify

## format: Format code (if formatter plugin is configured)
format:
	$(MVN) spotless:apply 2>/dev/null || echo "Spotless plugin not configured"

## deps: Download all dependencies
deps:
	$(MVN) dependency:resolve

## tree: Show dependency tree
tree:
	$(MVN) dependency:tree
