#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${TEST_COMPOSE_FILE:-docker-compose.test.yml}"
TEST_PROJECT_NAME="${TEST_PROJECT_NAME:-tibiachrono-test}"
DB_SERVICE="${TEST_DB_SERVICE:-db-test}"
DB_CONTAINER="${TEST_DB_CONTAINER:-tibiachrono-db-test}"
MAVEN_IMAGE="${TEST_MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-21}"
TEST_WORK_DIR="${TEST_WORK_DIR:-.test-maven}"
TEST_NETWORK="${TEST_NETWORK:-${TEST_PROJECT_NAME}_default}"
MAVEN_ARGS="${MAVEN_ARGS:--U clean test}"
TEST_RUNNER_CONTAINER="${TEST_RUNNER_CONTAINER:-tibiachrono-maven-test-runner}"

compose_test() {
  docker compose -p "$TEST_PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
}

usage() {
  cat <<'EOF_USAGE'
Usage:
  ./run-tests.sh           Start isolated test DB and run tests inside a copied Maven Docker workspace
  ./run-tests.sh run       Same as above
  ./run-tests.sh test      Same as above
  ./run-tests.sh host      Run tests with host Maven against localhost:5433 (touches ./target)
  ./run-tests.sh down      Stop and remove test DB container/network
  ./run-tests.sh clean     Stop/remove test DB and remove local dockerized test workspace
  ./run-tests.sh logs      Show test DB logs

Default behavior:
  - Does NOT stop the dev app container.
  - Does NOT use host Maven.
  - Does NOT clean or write the project ./target directory.
  - Copies the source tree to .test-maven/workspace and runs Maven there.
  - Test reports/classes are written under .test-maven/workspace/target.
  - Maven dependencies are cached under .test-maven/m2.
  - Local patch scripts under ./patches are not copied to the isolated Maven workspace.

Useful env overrides:
  TEST_MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
  MAVEN_ARGS="-U clean test"
  TEST_WORK_DIR=.test-maven
EOF_USAGE
}

ensure_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: Docker is required for the default test runner." >&2
    echo "Install/start Docker, or run './run-tests.sh host' if Maven is available on the host." >&2
    exit 1
  fi
}

validate_test_work_dir() {
  case "$TEST_WORK_DIR" in
    ""|"/"|".")
      echo "ERROR: unsafe TEST_WORK_DIR: '$TEST_WORK_DIR'" >&2
      exit 1
      ;;
    /*)
      echo "ERROR: TEST_WORK_DIR must be a relative path, got: '$TEST_WORK_DIR'" >&2
      exit 1
      ;;
    *..*)
      echo "ERROR: TEST_WORK_DIR must not contain '..', got: '$TEST_WORK_DIR'" >&2
      exit 1
      ;;
  esac
}

start_test_db() {
  compose_test up -d --remove-orphans "$DB_SERVICE"

  echo "Waiting for PostgreSQL test database..."
  for i in {1..60}; do
    if docker exec "$DB_CONTAINER" pg_isready -U tibia -d tibiastats_test >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
    if [ "$i" = "60" ]; then
      echo "PostgreSQL test database did not become ready in time" >&2
      compose_test logs "$DB_SERVICE" >&2
      exit 1
    fi
  done
}

repair_test_workspace_permissions() {
  if [ ! -e "$TEST_WORK_DIR" ]; then
    return 0
  fi

  chmod -R u+rwX "$TEST_WORK_DIR" 2>/dev/null || true

  if find "$TEST_WORK_DIR" ! -user "$(id -u)" -print -quit 2>/dev/null | grep -q .; then
    docker run --rm \
      -v "$PWD:/repo" \
      -w /repo \
      "$MAVEN_IMAGE" \
      sh -c "chown -R $(id -u):$(id -g) '$TEST_WORK_DIR' 2>/dev/null || true" \
      >/dev/null 2>&1 || true
  fi

  chmod -R u+rwX "$TEST_WORK_DIR" 2>/dev/null || true
}

prepare_dockerized_workspace() {
  validate_test_work_dir
  repair_test_workspace_permissions

  local workspace_dir="$TEST_WORK_DIR/workspace"
  local m2_dir="$TEST_WORK_DIR/m2"

  rm -rf "$workspace_dir"
  mkdir -p "$workspace_dir" "$m2_dir"

  echo "Copying project to isolated test workspace: $workspace_dir"
  tar \
    --exclude='./target' \
    --exclude="./$TEST_WORK_DIR" \
    --exclude='./.git' \
    --exclude='./.idea' \
    --exclude='./patches' \
    --exclude='./patches/**' \
    --exclude='./*.log' \
    -cf - . | tar -xf - -C "$workspace_dir"
}

run_tests_in_maven_container() {
  prepare_dockerized_workspace

  local workspace_dir="$PWD/$TEST_WORK_DIR/workspace"
  local m2_dir="$PWD/$TEST_WORK_DIR/m2"

  echo "Running tests in isolated Maven container. Dev app will keep running if it is up."
  echo "Project ./target will not be touched."
  echo "Maven reports/classes will be written under: $TEST_WORK_DIR/workspace/target"

  docker rm -f "$TEST_RUNNER_CONTAINER" >/dev/null 2>&1 || true

  set +e
  # shellcheck disable=SC2086
  docker run --rm \
    --name "$TEST_RUNNER_CONTAINER" \
    --network "$TEST_NETWORK" \
    -e SPRING_PROFILES_ACTIVE=test \
    -e TEST_DB_URL="${TEST_DB_URL:-jdbc:postgresql://${DB_SERVICE}:5432/tibiastats_test}" \
    -e TEST_DB_USERNAME="${TEST_DB_USERNAME:-tibia}" \
    -e TEST_DB_PASSWORD="${TEST_DB_PASSWORD:-secret}" \
    -v "$workspace_dir:/workspace" \
    -v "$m2_dir:/root/.m2" \
    -w /workspace \
    "$MAVEN_IMAGE" \
    mvn $MAVEN_ARGS
  local status=$?
  set -e

  repair_test_workspace_permissions
  return "$status"
}

prepare_target_permissions_for_host_maven() {
  if [ -d target ]; then
    chmod -R u+rwX target 2>/dev/null || true
    if find target ! -user "$(id -u)" -print -quit 2>/dev/null | grep -q .; then
      echo "WARNING: target contains files not owned by the current user." >&2
      echo "Host Maven may fail during clean. Recommended options:" >&2
      echo "  1) Use the default dockerized runner: ./run-tests.sh" >&2
      echo "  2) Or repair once: sudo chown -R $USER:$USER target && rm -rf target" >&2
    fi
  fi
}

run_tests_on_host() {
  if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: Maven is not available on the host. Use './run-tests.sh' for Dockerized Maven." >&2
    exit 1
  fi

  export SPRING_PROFILES_ACTIVE=test
  export TEST_DB_URL="${TEST_DB_URL:-jdbc:postgresql://localhost:5433/tibiastats_test}"
  export TEST_DB_USERNAME="${TEST_DB_USERNAME:-tibia}"
  export TEST_DB_PASSWORD="${TEST_DB_PASSWORD:-secret}"

  prepare_target_permissions_for_host_maven
  # shellcheck disable=SC2086
  mvn $MAVEN_ARGS
}

cmd="${1:-run}"

case "$cmd" in
  run|test)
    ensure_docker
    start_test_db
    run_tests_in_maven_container
    ;;

  host)
    ensure_docker
    start_test_db
    run_tests_on_host
    ;;

  down|stop)
    ensure_docker
    compose_test down --remove-orphans
    ;;

  clean|down-clean)
    ensure_docker
    compose_test down -v --remove-orphans
    validate_test_work_dir
    repair_test_workspace_permissions
    rm -rf "$TEST_WORK_DIR"
    ;;

  logs)
    ensure_docker
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
