#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

backup() {
  local f="$1"
  if [ -f "$f" ]; then
    cp "$f" "$f.bak.$(date +%Y%m%d%H%M%S)"
  fi
}

[ -f pom.xml ] || { echo "ERROR: run from project root (pom.xml not found)." >&2; exit 1; }

backup run-tests.sh
backup Makefile
backup docker-compose.dev.yml
backup src/main/resources/application.yml
backup src/main/resources/application-dev.yml
backup src/main/java/com/nathan/tibiastats/config/SecurityConfig.java

python3 - <<'PY'
from pathlib import Path


def indent_of(line: str) -> int:
    return len(line) - len(line.lstrip(' '))


def find_key(lines, key, indent, start=0, end=None):
    end = len(lines) if end is None else end
    prefix = ' ' * indent + key + ':'
    for i in range(start, end):
        if lines[i].startswith(prefix):
            return i
    return -1


def block_end(lines, start):
    if start < 0:
        return -1
    ind = indent_of(lines[start])
    i = start + 1
    while i < len(lines):
        s = lines[i]
        if s.strip() and indent_of(s) <= ind:
            break
        i += 1
    return i


def ensure_prop(lines, block_start, prop, value, prop_indent):
    end = block_end(lines, block_start)
    prefix = ' ' * prop_indent + prop + ':'
    for i in range(block_start + 1, end):
        if lines[i].startswith(prefix):
            lines[i] = ' ' * prop_indent + f'{prop}: {value}\n'
            return lines
    lines.insert(block_start + 1, ' ' * prop_indent + f'{prop}: {value}\n')
    return lines


def ensure_scraper_yaml(path: Path):
    if not path.exists():
        return
    lines = path.read_text().splitlines(True)

    tib = find_key(lines, 'tibiastats', 0)
    if tib == -1:
        lines.extend([
            '\n',
            'tibiastats:\n',
            '  scrape:\n',
            '    worlds:\n',
            '      enabled: true\n',
            '      rate-ms: 60000\n',
            '    character-details:\n',
            '      enabled: true\n',
            '      rate-ms: 60000\n',
            '      initial-delay-ms: 15000\n',
            '      batch-size: 50\n',
            '    guilds:\n',
            '      enabled: false\n',
        ])
        path.write_text(''.join(lines))
        return

    tib_end = block_end(lines, tib)
    scrape = find_key(lines, 'scrape', 2, tib + 1, tib_end)
    if scrape == -1:
        lines.insert(tib + 1, '  scrape:\n')
        scrape = tib + 1
    scrape_end = block_end(lines, scrape)

    worlds = find_key(lines, 'worlds', 4, scrape + 1, scrape_end)
    if worlds == -1:
        lines.insert(scrape + 1, '    worlds:\n')
        worlds = scrape + 1
        scrape_end += 1
    lines = ensure_prop(lines, worlds, 'enabled', 'true', 6)
    worlds_end = block_end(lines, worlds)
    # Ensure rate-ms exists for worlds.
    lines = ensure_prop(lines, worlds, 'rate-ms', '60000', 6)
    worlds_end = block_end(lines, worlds)
    scrape_end = block_end(lines, scrape)

    char = find_key(lines, 'character-details', 4, scrape + 1, scrape_end)
    if char == -1:
        insert_at = worlds_end
        char_block = [
            '    character-details:\n',
            '      enabled: true\n',
            '      rate-ms: 60000\n',
            '      initial-delay-ms: 15000\n',
            '      batch-size: 50\n',
        ]
        lines[insert_at:insert_at] = char_block
        char = insert_at
    else:
        lines = ensure_prop(lines, char, 'enabled', 'true', 6)
        lines = ensure_prop(lines, char, 'rate-ms', '60000', 6)
        lines = ensure_prop(lines, char, 'initial-delay-ms', '15000', 6)
        lines = ensure_prop(lines, char, 'batch-size', '50', 6)
    scrape_end = block_end(lines, scrape)

    guilds = find_key(lines, 'guilds', 4, scrape + 1, scrape_end)
    if guilds == -1:
        # Insert guilds disabled after character-details.
        char = find_key(lines, 'character-details', 4, scrape + 1, block_end(lines, scrape))
        insert_at = block_end(lines, char) if char != -1 else block_end(lines, worlds)
        guild_block = [
            '    guilds:\n',
            '      enabled: false\n',
            '      rate-ms: 3600000\n',
            '      initial-delay-ms: 30000\n',
            '      world-limit: 0\n',
            '      guild-limit: 50\n',
            '      page-delay-ms: 750\n',
            '      list-enabled: true\n',
            '      details-enabled: true\n',
        ]
        lines[insert_at:insert_at] = guild_block
    else:
        lines = ensure_prop(lines, guilds, 'enabled', 'false', 6)

    path.write_text(''.join(lines))


for file_name in ['src/main/resources/application.yml', 'src/main/resources/application-dev.yml']:
    ensure_scraper_yaml(Path(file_name))

# docker-compose.dev.yml: explicit env flags for expected dev behavior.
compose = Path('docker-compose.dev.yml')
if compose.exists():
    lines = compose.read_text().splitlines(True)
    env_vars = [
        '      TIBIASTATS_SCRAPE_WORLDS_ENABLED: "true"\n',
        '      TIBIASTATS_SCRAPE_CHARACTER_DETAILS_ENABLED: "true"\n',
        '      TIBIASTATS_SCRAPE_GUILDS_ENABLED: "false"\n',
    ]
    existing = ''.join(lines)
    missing = [v for v in env_vars if v.split(':', 1)[0].strip() not in existing]
    if missing:
        insert_at = -1
        for i, line in enumerate(lines):
            if line.strip().startswith('SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_SECRET_KEY:'):
                insert_at = i + 1
                break
        if insert_at == -1:
            for i, line in enumerate(lines):
                if line.startswith('    environment:'):
                    insert_at = i + 1
                    break
        if insert_at != -1:
            lines[insert_at:insert_at] = missing
            compose.write_text(''.join(lines))

# SecurityConfig: ensure custom JWT/Bearer security exists.
sec = Path('src/main/java/com/nathan/tibiastats/config/SecurityConfig.java')
sec.parent.mkdir(parents=True, exist_ok=True)
current = sec.read_text() if sec.exists() else ''
if not ('SecurityFilterChain' in current and 'oauth2ResourceServer' in current and 'JwtDecoder' in current):
    sec.write_text('''package com.nathan.tibiastats.config;

import com.nathan.tibiastats.application.service.TokenService;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/actuator/**", "/auth/login", "/auth/refresh", "/auth/register").permitAll()
                        .requestMatchers("/api/**", "/graphql").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.secret-key}") String secretKey
    ) {
        return NimbusJwtDecoder
                .withSecretKey(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public TokenBlacklistFilter tokenBlacklistFilter(TokenService tokens, JwtService jwt) {
        return new TokenBlacklistFilter(tokens, jwt);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
''')

# run-tests.sh: isolated compose project and stop dev app before Maven clean.
Path('run-tests.sh').write_text('''#!/usr/bin/env bash
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
      echo "  sudo chown -R \"$USER:$USER\" target" >&2
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
''')

mk = Path('Makefile')
if mk.exists():
    text = mk.read_text()
    if 'test-down:' not in text:
        text += '''

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
    mk.write_text(text)
PY

chmod +x run-tests.sh

echo "Applied dev scraper/test runner fix."
echo "If Maven clean failed before, run once:"
echo "  sudo chown -R \"$USER:$USER\" target 2>/dev/null || true"
echo "  rm -rf target"
echo "Then run:"
echo "  ./run-tests.sh"
echo "And restart the dev app:"
echo "  docker compose -f docker-compose.dev.yml up -d --build app"
