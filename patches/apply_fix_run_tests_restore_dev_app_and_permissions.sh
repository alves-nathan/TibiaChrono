#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

[ -f pom.xml ] || { echo "ERROR: run from project root (pom.xml not found)." >&2; exit 1; }

backup() {
  local f="$1"
  if [ -f "$f" ]; then
    cp "$f" "$f.bak.$(date +%Y%m%d%H%M%S)"
  fi
}

backup run-tests.sh
backup Makefile

cat > run-tests.sh <<'SCRIPT'
#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${TEST_COMPOSE_FILE:-docker-compose.test.yml}"
TEST_PROJECT_NAME="${TEST_PROJECT_NAME:-tibiachrono-test}"
DB_SERVICE="${TEST_DB_SERVICE:-db-test}"
DB_CONTAINER="${TEST_DB_CONTAINER:-tibiachrono-db-test}"
STOP_DEV_APP_FOR_TESTS="${STOP_DEV_APP_FOR_TESTS:-true}"
RESTART_DEV_APP_AFTER_TESTS="${RESTART_DEV_APP_AFTER_TESTS:-true}"
DEV_COMPOSE_FILE="${DEV_COMPOSE_FILE:-docker-compose.dev.yml}"
DEV_APP_STOPPED_BY_TEST_RUNNER="false"

compose_test() {
  docker compose -p "$TEST_PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
}

usage() {
  cat <<'USAGE'
Usage:
  ./run-tests.sh          Stop dev app if needed, start test DB, run Maven tests
  ./run-tests.sh run      Same as above
  ./run-tests.sh down     Stop and remove test DB container/network
  ./run-tests.sh clean    Stop/remove test DB and anonymous volumes/orphans
  ./run-tests.sh logs     Show test DB logs

Env:
  STOP_DEV_APP_FOR_TESTS=false       Keep dev app running, not recommended with mvn clean
  RESTART_DEV_APP_AFTER_TESTS=false  Do not restart dev app after this script stops it
  TEST_PROJECT_NAME=name             Compose project name for the isolated test DB
USAGE
}

restore_dev_app_if_needed() {
  local exit_code="$?"

  if [ "$DEV_APP_STOPPED_BY_TEST_RUNNER" = "true" ] && [ "$RESTART_DEV_APP_AFTER_TESTS" = "true" ]; then
    echo "Restarting dev app service that was stopped for tests..."
    if ! docker compose -f "$DEV_COMPOSE_FILE" up -d app >/dev/null 2>&1; then
      echo "WARNING: failed to restart dev app automatically." >&2
      echo "Restart manually with: docker compose -f $DEV_COMPOSE_FILE up -d app" >&2
    else
      echo "Dev app restarted."
    fi
  fi

  exit "$exit_code"
}

stop_dev_app_if_running() {
  if [ "$STOP_DEV_APP_FOR_TESTS" != "true" ]; then
    return 0
  fi
  if [ ! -f "$DEV_COMPOSE_FILE" ]; then
    return 0
  fi
  if docker compose -f "$DEV_COMPOSE_FILE" ps --services --filter "status=running" 2>/dev/null | grep -qx "app"; then
    echo "Stopping dev app service before tests to avoid target/classes locks during mvn clean..."
    docker compose -f "$DEV_COMPOSE_FILE" stop app >/dev/null
    DEV_APP_STOPPED_BY_TEST_RUNNER="true"
    echo "Dev app stopped temporarily. It will be restarted when tests finish or fail."
  fi
}

target_has_foreign_owner() {
  [ -d target ] && find target ! -user "$(id -u)" -print -quit 2>/dev/null | grep -q .
}

repair_target_permissions_with_docker() {
  if [ ! -f "$DEV_COMPOSE_FILE" ]; then
    return 1
  fi

  local host_uid host_gid
  host_uid="$(id -u)"
  host_gid="$(id -g)"

  echo "Fixing target/ ownership via Docker because it contains files not owned by $(id -un)..."
  docker compose -f "$DEV_COMPOSE_FILE" run --rm --no-deps --user root --entrypoint sh app \
    -lc "if [ -d /workspace/target ]; then chown -R ${host_uid}:${host_gid} /workspace/target; fi" >/dev/null
}

prepare_target_permissions() {
  if [ ! -d target ]; then
    return 0
  fi

  local chmod_failed="false"
  chmod -R u+rwX target 2>/dev/null || chmod_failed="true"

  if [ "$chmod_failed" = "true" ] || target_has_foreign_owner; then
    if ! repair_target_permissions_with_docker; then
      echo "WARNING: automatic target/ ownership repair failed." >&2
    fi
  fi

  chmod_failed="false"
  chmod -R u+rwX target 2>/dev/null || chmod_failed="true"

  if [ "$chmod_failed" = "true" ] || target_has_foreign_owner; then
    echo "ERROR: target/ still contains files not owned by the current user." >&2
    echo "Run this once and try again:" >&2
    echo "  sudo chown -R \"$USER:$USER\" target" >&2
    echo "  rm -rf target" >&2
    return 1
  fi
}

wait_for_test_database() {
  echo "Waiting for PostgreSQL test database..."
  for i in {1..60}; do
    if docker exec "$DB_CONTAINER" pg_isready -U tibia -d tibiastats_test >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  echo "PostgreSQL test database did not become ready in time" >&2
  compose_test logs "$DB_SERVICE" >&2
  return 1
}

run_tests() {
  trap restore_dev_app_if_needed EXIT

  stop_dev_app_if_running
  prepare_target_permissions

  compose_test up -d --remove-orphans "$DB_SERVICE"
  wait_for_test_database

  export SPRING_PROFILES_ACTIVE=test
  export TEST_DB_URL="jdbc:postgresql://localhost:5433/tibiastats_test"
  export TEST_DB_USERNAME="tibia"
  export TEST_DB_PASSWORD="secret"

  mvn -U clean test
}

cmd="${1:-run}"

case "$cmd" in
  run|test)
    run_tests
    ;;

  down|stop)
    compose_test down --remove-orphans
    ;;

  clean|down-clean)
    compose_test down -v --remove-orphans
    ;;

  logs)
    compose_test logs -f "$DB_SERVICE"
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
SCRIPT

chmod +x run-tests.sh

python3 - <<'PY'
from pathlib import Path

path = Path('Makefile')
if path.exists():
    text = path.read_text()
    replacements = {
        '\n\trun ./run-tests.sh\n': '\n\t./run-tests.sh\n',
        '\n\trun ./run-tests.sh down\n': '\n\t./run-tests.sh down\n',
        '\n\trun ./run-tests.sh clean\n': '\n\t./run-tests.sh clean\n',
        '\n\trun ./run-tests.sh logs\n': '\n\t./run-tests.sh logs\n',
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    path.write_text(text)
PY

echo "Applied run-tests reliability fix."
echo "What changed:"
echo "  - run-tests.sh now restarts the dev app when tests finish or fail."
echo "  - run-tests.sh repairs root-owned target/ files using Docker before mvn clean."
echo "  - Makefile test targets now call ./run-tests.sh directly instead of 'run ./run-tests.sh'."
echo
echo "Run:"
echo "  ./run-tests.sh"
