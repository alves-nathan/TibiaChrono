#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

if [ ! -f "Makefile" ]; then
  echo "Makefile not found. Run this script from the project root." >&2
  exit 1
fi

if [ ! -f "run-tests.sh" ]; then
  echo "run-tests.sh not found. Run this script from the project root after applying the test infrastructure patch." >&2
  exit 1
fi

cp Makefile Makefile.bak.$(date +%Y%m%d%H%M%S)
cp run-tests.sh run-tests.sh.bak.$(date +%Y%m%d%H%M%S)

python3 - <<'PY'
from pathlib import Path

# Update run-tests.sh with command support.
run_tests = Path('run-tests.sh')
run_tests.write_text('''#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${TEST_COMPOSE_FILE:-docker-compose.test.yml}"
DB_SERVICE="${TEST_DB_SERVICE:-db-test}"
DB_CONTAINER="${TEST_DB_CONTAINER:-tibiachrono-db-test}"

usage() {
  cat <<'EOF'
Usage:
  ./run-tests.sh          Start the test database and run Maven tests
  ./run-tests.sh run      Start the test database and run Maven tests
  ./run-tests.sh down     Stop and remove the test database container/network
  ./run-tests.sh clean    Stop and remove the test database container/network and anonymous volumes
  ./run-tests.sh logs     Show test database logs
EOF
}

cmd="${1:-run}"

case "$cmd" in
  run|test)
    docker compose -f "$COMPOSE_FILE" up -d "$DB_SERVICE"

    echo "Waiting for PostgreSQL test database..."
    for i in {1..60}; do
      if docker exec "$DB_CONTAINER" pg_isready -U tibia -d tibiastats_test >/dev/null 2>&1; then
        break
      fi
      sleep 1
      if [ "$i" = "60" ]; then
        echo "PostgreSQL test database did not become ready in time" >&2
        docker compose -f "$COMPOSE_FILE" logs "$DB_SERVICE" >&2
        exit 1
      fi
    done

    export SPRING_PROFILES_ACTIVE=test
    export TEST_DB_URL="jdbc:postgresql://localhost:5433/tibiastats_test"
    export TEST_DB_USERNAME="tibia"
    export TEST_DB_PASSWORD="secret"

    mvn -U clean test
    ;;

  down|stop)
    docker compose -f "$COMPOSE_FILE" down
    ;;

  clean|down-clean)
    docker compose -f "$COMPOSE_FILE" down -v --remove-orphans
    ;;

  logs)
    docker compose -f "$COMPOSE_FILE" logs -f "$DB_SERVICE"
    ;;

  help|-h|--help)
    usage
    ;;

  *)
    echo "Unknown command: $cmd" >&2
    usage >&2
    exit 2
    ;;
esac
''')

# Update Makefile.
makefile = Path('Makefile')
text = makefile.read_text()
if 'TEST_COMPOSE :=' not in text and 'PROD_COMPOSE := docker-compose.yml' in text:
    text = text.replace('PROD_COMPOSE := docker-compose.yml\n', 'PROD_COMPOSE := docker-compose.yml\nTEST_COMPOSE := docker-compose.test.yml\n')

text = text.replace('@echo "  make test          - run Maven tests on host"\n\t@echo "  make test-dev      - run Maven tests INSIDE dev container"',
'''@echo "  make test          - run full test suite using isolated test database"
	@echo "  make test-dev      - run Maven tests INSIDE dev container"
	@echo "  make test-down     - stop test database container/network"
	@echo "  make test-clean    - stop test database and remove anonymous volumes"
	@echo "  make test-logs     - tail test database logs"''')

if '# ---- Tests ----' in text and '# ---- DB & Volumes ----' in text:
    start = text.index('# ---- Tests ----')
    end = text.index('# ---- DB & Volumes ----')
    section = '''# ---- Tests ----
.PHONY: test
test:
	./run-tests.sh

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

'''
    text = text[:start] + section + text[end:]
else:
    append = '''
# ---- Tests ----
.PHONY: test
test:
	./run-tests.sh

.PHONY: test-down
test-down:
	./run-tests.sh down

.PHONY: test-clean
test-clean:
	./run-tests.sh clean

.PHONY: test-logs
test-logs:
	./run-tests.sh logs
'''
    if 'test-down:' not in text:
        text += append

makefile.write_text(text)
PY

chmod +x run-tests.sh

echo "Done. Available commands:"
echo "  ./run-tests.sh down"
echo "  ./run-tests.sh clean"
echo "  make test-down"
echo "  make test-clean"
