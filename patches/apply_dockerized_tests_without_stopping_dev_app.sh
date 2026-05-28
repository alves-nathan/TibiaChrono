#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(pwd)"
PATCH_NAME="dockerized-tests-without-stopping-dev-app"
STAMP="$(date +%Y%m%d%H%M%S)"

if [ ! -f "pom.xml" ] || [ ! -f "docker-compose.test.yml" ]; then
  echo "ERROR: run this patch from the TibiaChrono project root." >&2
  exit 1
fi

backup_file() {
  local file="$1"
  if [ -f "$file" ] && [ ! -f "$file.bak.$STAMP" ]; then
    cp "$file" "$file.bak.$STAMP"
  fi
}

backup_file "run-tests.sh"
backup_file "Makefile"
backup_file ".gitignore"

cat > run-tests.sh <<'RUN_TESTS_EOF'
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

compose_test() {
  docker compose -p "$TEST_PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
}

usage() {
  cat <<'EOF_USAGE'
Usage:
  ./run-tests.sh           Start isolated test DB and run tests inside a Maven Docker container
  ./run-tests.sh run       Same as above
  ./run-tests.sh test      Same as above
  ./run-tests.sh host      Run tests with host Maven against localhost:5433 (touches ./target)
  ./run-tests.sh down      Stop and remove test DB container/network
  ./run-tests.sh clean     Stop/remove test DB and remove local dockerized test workspace
  ./run-tests.sh logs      Show test DB logs

Default behavior:
  - Does NOT stop the dev app container.
  - Does NOT use host Maven.
  - Does NOT clean or write the host ./target directory.
  - Uses .test-maven/target and .test-maven/m2 as local isolated test workspace.

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

prepare_dockerized_workspace() {
  mkdir -p "$TEST_WORK_DIR/target" "$TEST_WORK_DIR/m2" "$TEST_WORK_DIR/home"
  chmod -R u+rwX "$TEST_WORK_DIR" 2>/dev/null || true
}

run_tests_in_maven_container() {
  prepare_dockerized_workspace

  echo "Running tests in isolated Maven container. Dev app will keep running if it is up."
  echo "Maven reports/classes will be written under: $TEST_WORK_DIR/target"

  # shellcheck disable=SC2086
  docker run --rm \
    --name "${TEST_RUNNER_CONTAINER:-tibiachrono-maven-test-runner}" \
    --network "$TEST_NETWORK" \
    --user "$(id -u):$(id -g)" \
    -e SPRING_PROFILES_ACTIVE=test \
    -e TEST_DB_URL="${TEST_DB_URL:-jdbc:postgresql://${DB_SERVICE}:5432/tibiastats_test}" \
    -e TEST_DB_USERNAME="${TEST_DB_USERNAME:-tibia}" \
    -e TEST_DB_PASSWORD="${TEST_DB_PASSWORD:-secret}" \
    -e MAVEN_CONFIG="/workspace/${TEST_WORK_DIR}/home/.m2" \
    -v "$PWD:/workspace" \
    -v "$PWD/${TEST_WORK_DIR}/target:/workspace/target" \
    -v "$PWD/${TEST_WORK_DIR}/m2:/workspace/${TEST_WORK_DIR}/home/.m2/repository" \
    -w /workspace \
    "$MAVEN_IMAGE" \
    mvn -Dmaven.repo.local="/workspace/${TEST_WORK_DIR}/home/.m2/repository" \
        -Duser.home="/workspace/${TEST_WORK_DIR}/home" \
        $MAVEN_ARGS
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
RUN_TESTS_EOF
chmod +x run-tests.sh

python3 - <<'PY'
from pathlib import Path
import re

path = Path('Makefile')
text = path.read_text()

# Fix old invalid Makefile commands.
text = text.replace('\trun ./run-tests.sh', '\t./run-tests.sh')

# Normalize help lines so the patch is safe to re-run.
text = re.sub(r'\n\t@echo "  make up-dev-verified[^\n]*"', '', text)
text = re.sub(r'\n\t@echo "  make test-host[^\n]*"', '', text)
text = re.sub(
    r'\t@echo "  make up-dev\s+- [^\n]*"',
    '\t@echo "  make up-dev        - build & start dev (hot-reload) compose"\n'
    '\t@echo "  make up-dev-verified - run tests, then build & start dev compose"',
    text,
)
text = re.sub(
    r'\t@echo "  make test\s+- [^\n]*"',
    '\t@echo "  make test          - run full test suite in isolated Maven container"\n'
    '\t@echo "  make test-host     - run full test suite with host Maven"',
    text,
)

# Remove any previous generated blocks, then insert one canonical block.
up_dev_verified_block = (
    '.PHONY: up-dev-verified\n'
    'up-dev-verified: test\n'
    '\tSPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_SECRET_KEY=$(JWT_SECRET) docker compose -f $(DEV_COMPOSE) up --build\n\n'
)
text = re.sub(
    r'\n\.PHONY: up-dev-verified\nup-dev-verified: test\n\tSPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_SECRET_KEY=\$\(JWT_SECRET\) docker compose -f \$\(DEV_COMPOSE\) up --build\n',
    '\n',
    text,
)
text = text.replace('\n.PHONY: down-dev\n', '\n' + up_dev_verified_block + '.PHONY: down-dev\n')

# Normalize test-host target.
test_host_block = '.PHONY: test-host\ntest-host:\n\t./run-tests.sh host\n\n'
text = re.sub(r'\n\.PHONY: test-host\ntest-host:\n\t\.\/run-tests\.sh host\n', '\n', text)
text = text.replace('\n.PHONY: test-down\n', '\n' + test_host_block + '.PHONY: test-down\n')

path.write_text(text)
PY
if ! grep -qxF '/.test-maven/' .gitignore; then
  cat >> .gitignore <<'GITIGNORE_EOF'

# Local workspace used by ./run-tests.sh dockerized Maven runner
/.test-maven/
GITIGNORE_EOF
fi

bash -n run-tests.sh

cat <<'DONE'
Patch applied successfully.

What changed:
- ./run-tests.sh now runs Maven tests inside an isolated Docker container by default.
- The dev app is no longer stopped before tests.
- Host ./target is no longer cleaned or written by the default test runner.
- Test build output goes to .test-maven/target.
- Maven cache goes to .test-maven/m2.
- Makefile targets were fixed and test-host/up-dev-verified were added.

Run:
  ./run-tests.sh

Optional:
  make test
  make test-host
  make up-dev-verified
  ./run-tests.sh clean
DONE
