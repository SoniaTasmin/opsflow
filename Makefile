# OpsFlow developer entry points.
#
# Everything here must work from a clean clone. If a target needs a milestone
# that is not built yet, it says so rather than failing obscurely.

SHELL := /bin/bash
.DEFAULT_GOAL := help

COMPOSE := docker compose
GRADLEW := ./gradlew

.PHONY: help
help: ## Show available targets
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

.PHONY: preflight
preflight: ## Check that the required local tooling is present
	@ok=0; \
	command -v docker >/dev/null 2>&1 || { echo "MISSING: docker"; ok=1; }; \
	$(COMPOSE) version >/dev/null 2>&1 || { echo "MISSING: the 'docker compose' v2 plugin"; ok=1; }; \
	command -v java >/dev/null 2>&1 || { echo "MISSING: a Java 21 JDK (JAVA_HOME unset?)"; ok=1; }; \
	command -v node >/dev/null 2>&1 || { echo "MISSING: node (>= 24.15.0)"; ok=1; }; \
	if [ $$ok -eq 0 ]; then echo "preflight: all required tooling present"; \
	else echo; echo "See docs/local-development.md for installation notes."; exit 1; fi

.PHONY: up
up: ## Start infrastructure (PostgreSQL). Backend and frontend run from your IDE.
	@test -f .env || { echo "Creating .env from .env.example"; cp .env.example .env; }
	$(COMPOSE) up -d
	@echo "Waiting for PostgreSQL to report healthy..."
	@for i in $$(seq 1 30); do \
		status=$$($(COMPOSE) ps --format json postgres 2>/dev/null | grep -o '"Health":"[^"]*"' | head -1); \
		case "$$status" in *healthy*) echo "PostgreSQL is healthy"; exit 0;; esac; \
		sleep 2; \
	done; \
	echo "PostgreSQL did not become healthy in 60s"; $(COMPOSE) logs postgres | tail -30; exit 1

.PHONY: down
down: ## Stop the stack, keeping data volumes
	$(COMPOSE) down

.PHONY: clean
clean: ## Stop the stack and DELETE all local data, plus build output
	$(COMPOSE) down -v
	cd backend && $(GRADLEW) clean || true
	rm -rf frontend/dist frontend/.angular

.PHONY: test
test: test-backend test-frontend ## Run all tests

.PHONY: test-backend
test-backend: ## Run backend tests (unit + ArchUnit)
	cd backend && $(GRADLEW) test

.PHONY: test-frontend
test-frontend: ## Run frontend tests
	cd frontend && npm run test:ci

.PHONY: lint
lint: lint-backend lint-frontend ## Run all linters and format checks

.PHONY: lint-backend
lint-backend: ## Check Java formatting (Spotless)
	cd backend && $(GRADLEW) spotlessCheck

.PHONY: format
format: ## Auto-fix formatting in both projects
	cd backend && $(GRADLEW) spotlessApply
	cd frontend && npm run format

.PHONY: lint-frontend
lint-frontend: ## Run ESLint and check Prettier formatting
	cd frontend && npm run lint && npm run format:check

.PHONY: build
build: ## Build both projects
	cd backend && $(GRADLEW) build
	cd frontend && npm run build

.PHONY: demo
demo: ## Full demo stack with seed data
	@echo "Not available yet: the demo stack lands at M9."
	@echo "For now: 'make up' starts PostgreSQL and the app runs from your IDE."
	@exit 1
