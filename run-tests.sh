#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${TEST_COMPOSE_FILE:-docker-compose.test.yml}"
TEST_PROJECT_NAME="${TEST_PROJECT_NAME:-tibiachrono-test}"
DB_SERVICE="${TEST_DB_SERVICE:-db-test}"
DB_CONTAINER="${TEST_DB_CONTAINER:-tibiachrono-db-test}"
STOP_DEV_APP_FOR_TESTS="${STOP_DEV_APP_FOR_TESTS:-true}"

compose_test() {
  docker compose -p "$TEST_PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
}

usage() {
  cat <<'EOF'
Usage:
  ./run-tests.sh          Stop dev app if needed, start test DB, run Maven tests
  ./run-tests.sh run      Same as above
  ./run-tests.sh down     Stop and remove test DB container/network
  ./run-tests.sh clean    Stop/remove test DB and anonymous volumes/orphans
  ./run-tests.sh logs     Show test DB logs

Env:
  STOP_DEV_APP_FOR_TESTS=false  Keep dev app running, not recommended with mvn clean
EOF
}

stop_dev_app_if_running() {
  if [ "$STOP_DEV_APP_FOR_TESTS" != "true" ]; then
    return 0
  fi
  if [ ! -f docker-compose.dev.yml ]; then
    return 0
  fi
  if docker compose -f docker-compose.dev.yml ps --services --filter "status=running" 2>/dev/null | grep -qx "app"; then
    echo "Stopping dev app service before tests to avoid target/classes locks during mvn clean..."
    docker compose -f docker-compose.dev.yml stop app >/dev/null
    echo "Dev app stopped. Restart later with: docker compose -f docker-compose.dev.yml up -d app"
  fi
}

prepare_target_permissions() {
  if [ -d target ]; then
    chmod -R u+rwX target 2>/dev/null || true
    if find target ! -user "$(id -u)" -print -quit 2>/dev/null | grep -q .; then
      echo "WARNING: target contains files not owned by the current user." >&2
      echo "If Maven clean fails, run:" >&2
      echo "  sudo chown -R "$USER:$USER" target" >&2
      echo "  rm -rf target" >&2
    fi
  fi
}

cmd="${1:-run}"

case "$cmd" in
  run|test)
    stop_dev_app_if_running
    compose_test up -d --remove-orphans "$DB_SERVICE"

    echo "Waiting for PostgreSQL test database..."
    for i in {1..60}; do
      if docker exec "$DB_CONTAINER" pg_isready -U tibia -d tibiastats_test >/dev/null 2>&1; then
        break
      fi
      sleep 1
      if [ "$i" = "60" ]; then
        echo "PostgreSQL test database did not become ready in time" >&2
        compose_test logs "$DB_SERVICE" >&2
        exit 1
      fi
    done

    export SPRING_PROFILES_ACTIVE=test
    export TEST_DB_URL="jdbc:postgresql://localhost:5433/tibiastats_test"
    export TEST_DB_USERNAME="tibia"
    export TEST_DB_PASSWORD="secret"

    prepare_target_permissions
    mvn -U clean test
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
