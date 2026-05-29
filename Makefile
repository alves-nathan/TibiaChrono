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
	@echo "  make up-dev-verified - run tests, then build & start dev compose"
	@echo "  make down-dev      - stop dev compose (keep volumes)"
	@echo "  make down-dev-clean - stop dev compose and remove volumes"
	@echo "  make logs-dev      - tail dev app logs"
	@echo "  make test          - run full test suite in isolated copied Maven workspace"
	@echo "  make test-coverage - run tests and open JaCoCo HTML coverage report"
	@echo "  make test-host     - run full test suite with host Maven"
	@echo "  make test-dev      - run Maven tests INSIDE dev container"
	@echo "  make test-down     - stop test database container/network"
	@echo "  make test-clean    - stop test database and remove anonymous volumes"
	@echo "  make test-logs     - tail test database logs"
	@echo "  make db-psql       - open psql inside db container"
	@echo "  make clean-vol     - remove named volumes (DB data)"
	@echo "  make jwt-secret    - generate a long random JWT secret"
	@echo "  make env-print     - show important env vars"
	@echo "  make qa            - run full quality gate: tests, ArchUnit and JaCoCo check"
	@echo "  make arch-test     - run architecture fitness tests only"
	@echo "  make format        - apply Java formatter via Spotless"
	@echo "  make format-check  - verify Java formatting via Spotless"
	@echo "  make audit-worktree - list local/generated artifacts that should not be committed"
	@echo "  make clean-local-artifacts - remove local/generated artifacts from the working tree"
	@echo "  make export-clean   - create a clean ZIP export without local artifacts"
	@echo "  make code-health   - print class-size and architecture hotspot report"

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


.PHONY: up-dev-verified
up-dev-verified: test
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
	./run-tests.sh

.PHONY: test-coverage
test-coverage:
	MAVEN_ARGS="-U clean jacoco:prepare-agent test jacoco:report" ./run-tests.sh
	@report="$(PWD)/.test-maven/workspace/target/site/jacoco/index.html"; \
	echo "JaCoCo coverage report: $$report"; \
	if command -v wslview >/dev/null 2>&1; then \
	  wslview "$$report"; \
	elif command -v xdg-open >/dev/null 2>&1; then \
	  xdg-open "$$report" >/dev/null 2>&1 || true; \
	elif command -v explorer.exe >/dev/null 2>&1 && command -v wslpath >/dev/null 2>&1; then \
	  explorer.exe "$$(wslpath -w "$$report")"; \
	else \
	  echo "Open the file above in your browser."; \
	fi

.PHONY: test-host
test-host:
	./run-tests.sh host

.PHONY: test-down
test-down:
	./run-tests.sh down

.PHONY: test-clean
test-clean:
	./run-tests.sh clean

.PHONY: test-logs
test-logs:
	./run-tests.sh logs

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

# ---- Repository hygiene ----
.PHONY: audit-worktree
audit-worktree:
	@echo "Local/generated artifacts currently present:"; \
	find . \
	  -path './.git' -prune -o \
	  -path './.idea' -print -prune -o \
	  -path './.test-maven' -print -prune -o \
	  -path './target' -print -prune -o \
	  -path './patches/.backups' -print -prune -o \
	  \( -name '*.bak' -o -name '*.bak-*' -o -name '*.bak.*' -o -name '*Zone.Identifier*' \) -print | sort

.PHONY: clean-local-artifacts
clean-local-artifacts:
	@echo "Removing local/generated artifacts..."; \
	rm -rf .idea .test-maven target patches/.backups; \
	find . \
	  -path './.git' -prune -o \
	  \( -name '*.bak' -o -name '*.bak-*' -o -name '*.bak.*' -o -name '*Zone.Identifier*' \) -type f -print0 | xargs -0 -r rm -f; \
	echo "Done."

.PHONY: export-clean
export-clean:
	./scripts/export-clean.sh

.PHONY: code-health
code-health:
	./scripts/code-health-report.sh


# ---- Quality gates ----
.PHONY: qa
qa:
	MAVEN_ARGS="-U clean verify" ./run-tests.sh

.PHONY: arch-test
arch-test:
	MAVEN_ARGS="-U -Dtest=ArchitectureRulesTest test" ./run-tests.sh

.PHONY: format
format:
	MAVEN_ARGS="-U spotless:apply" ./run-tests.sh

.PHONY: format-check
format-check:
	MAVEN_ARGS="-U spotless:check" ./run-tests.sh
