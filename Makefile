# -------------------------------
# TibiaStats Makefile
# -------------------------------

SHELL := /bin/bash
APP  ?= tibiastats
DEV_COMPOSE := docker-compose.dev.yml
PROD_COMPOSE := docker-compose.yml
TEST_COMPOSE := docker-compose.test.yml

# Default env vars (override by exporting in your shell or .env if you like)
JWT_SECRET ?= please-change-me-to-a-very-long-random-secret

# ---- Helpers ----
.PHONY: help
help:
	@echo "Usage:"
	@echo "  make up            - build & start prod-like compose (detached)"
	@echo "  make down          - stop prod-like compose (keep volumes)"
	@echo "  make down-clean    - stop prod-like compose and remove volumes"
	@echo "  make logs          - tail prod-like app logs"
	@echo "  make rebuild       - rebuild prod-like app image"
	@echo "  make up-dev        - build & start dev (hot-reload) compose"
	@echo "  make down-dev      - stop dev compose (keep volumes)"
	@echo "  make down-dev-clean - stop dev compose and remove volumes"
	@echo "  make logs-dev      - tail dev app logs"
	@echo "  make test          - run full test suite using isolated test database"
	@echo "  make test-dev      - run Maven tests INSIDE dev container"
	@echo "  make test-down     - stop test database container/network"
	@echo "  make test-clean    - stop test database and remove anonymous volumes"
	@echo "  make test-logs     - tail test database logs"
	@echo "  make db-psql       - open psql inside db container"
	@echo "  make clean-vol     - remove named volumes (DB data)"
	@echo "  make jwt-secret    - generate a long random JWT secret"
	@echo "  make env-print     - show important env vars"

# ---- Prod-like ----
.PHONY: up
up:
	SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_SECRET_KEY=$(JWT_SECRET) docker compose -f $(PROD_COMPOSE) up --build -d

.PHONY: down
down:
	docker compose -f $(PROD_COMPOSE) down

.PHONY: down-clean
down-clean:
	docker compose -f $(PROD_COMPOSE) down -v

.PHONY: logs
logs:
	docker compose -f $(PROD_COMPOSE) logs -f app

.PHONY: rebuild
rebuild:
	docker compose -f $(PROD_COMPOSE) build --no-cache app

# ---- Dev (hot reload) ----
.PHONY: up-dev
up-dev:
	SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_SECRET_KEY=$(JWT_SECRET) docker compose -f $(DEV_COMPOSE) up --build

.PHONY: down-dev
down-dev:
	docker compose -f $(DEV_COMPOSE) down

.PHONY: down-dev-clean
down-dev-clean:
	docker compose -f $(DEV_COMPOSE) down -v

.PHONY: logs-dev
logs-dev:
	docker compose -f $(DEV_COMPOSE) logs -f app

# ---- Tests ----
.PHONY: test
test:
	run ./run-tests.sh

.PHONY: test-down
test-down:
	run ./run-tests.sh down

.PHONY: test-clean
test-clean:
	run ./run-tests.sh clean

.PHONY: test-logs
test-logs:
	run ./run-tests.sh logs

.PHONY: test-dev
test-dev:
	docker compose -f $(DEV_COMPOSE) exec app mvn -Dtest=*IntegrationTest test

# ---- DB & Volumes ----
.PHONY: db-psql
db-psql:
	@docker compose -f $(PROD_COMPOSE) exec -it db psql -U tibia -d tibiastats || \
	docker compose -f $(DEV_COMPOSE) exec -it db psql -U tibia -d tibiastats

.PHONY: clean-vol
clean-vol:
	@echo "WARNING: This deletes your Postgres data volume (db_data)."
	@read -p "Type 'YES' to continue: " ans; \
	if [ "$$ans" = "YES" ]; then \
	  docker volume rm $$(docker volume ls -q | grep db_data) || true; \
	else \
	  echo "Aborted."; \
	fi

# ---- Utilities ----
.PHONY: jwt-secret
jwt-secret:
	@python3 -c 'import secrets,base64; print(base64.urlsafe_b64encode(secrets.token_bytes(64)).decode())'

.PHONY: env-print
env-print:
	@echo "JWT_SECRET=$(JWT_SECRET)"
