#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${1:-.}"
cd "$PROJECT_DIR"

mkdir -p src/test/java/com/nathan/tibiastats src/test/resources

cat > src/test/java/com/nathan/tibiastats/AbstractPostgresTest.java <<'JAVA'
package com.nathan.tibiastats;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests.
 *
 * The project previously used Testcontainers directly, but Docker Desktop/WSL can expose
 * a socket that works for the Docker CLI while still failing Docker-Java/Testcontainers
 * detection. These tests now use an explicit, isolated PostgreSQL test database,
 * normally started with docker-compose.test.yml on localhost:5433.
 */
@ActiveProfiles("test")
public abstract class AbstractPostgresTest {
    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("TEST_DB_URL", "jdbc:postgresql://localhost:5433/tibiastats_test"));
        registry.add("spring.datasource.username", () -> env("TEST_DB_USERNAME", "tibia"));
        registry.add("spring.datasource.password", () -> env("TEST_DB_PASSWORD", "secret"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.security.oauth2.resourceserver.jwt.secret-key", () -> "test-secret-key-which-is-very-long-and-random-1234567890");
    }

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("""
                truncate table
                    highscore_exp_rank_daily,
                    highscore_exp_daily,
                    highscore_record_periods,
                    highscore_current_records,
                    highscore_scrape_scopes,
                    character_statrecords,
                    character_deaths,
                    character_names,
                    character_worlds,
                    scrape_players,
                    scrapes,
                    guild_characters,
                    guilds,
                    refresh_tokens,
                    token_blacklist,
                    users,
                    characters,
                    worlds
                restart identity cascade
                """);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
JAVA

cat > src/test/resources/application-test.yml <<'YAML'
spring:
  datasource:
    url: ${TEST_DB_URL:jdbc:postgresql://localhost:5433/tibiastats_test}
    username: ${TEST_DB_USERNAME:tibia}
    password: ${TEST_DB_PASSWORD:secret}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    show-sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  security:
    oauth2:
      resourceserver:
        jwt:
          secret-key: "test-secret-key-which-is-very-long-and-random-1234567890"

logging:
  level:
    root: WARN
    com.nathan.tibiastats: INFO

tibiastats:
  scrape:
    worlds:
      enabled: false
      rate-ms: 3600000
    character-details:
      enabled: false
      rate-ms: 3600000
      initial-delay-ms: 3600000
      batch-size: 0
    highscores:
      enabled: false
YAML

cat > docker-compose.test.yml <<'YAML'
services:
  db-test:
    image: postgres:15-alpine
    container_name: tibiachrono-db-test
    environment:
      POSTGRES_DB: tibiastats_test
      POSTGRES_USER: tibia
      POSTGRES_PASSWORD: secret
    ports:
      - "5433:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U tibia -d tibiastats_test"]
      interval: 2s
      timeout: 5s
      retries: 30
    tmpfs:
      - /var/lib/postgresql/data
YAML

cat > run-tests.sh <<'BASH'
#!/usr/bin/env bash
set -euo pipefail

docker compose -f docker-compose.test.yml up -d db-test

echo "Waiting for PostgreSQL test database..."
for i in {1..60}; do
  if docker exec tibiachrono-db-test pg_isready -U tibia -d tibiastats_test >/dev/null 2>&1; then
    break
  fi
  sleep 1
  if [ "$i" = "60" ]; then
    echo "PostgreSQL test database did not become ready in time" >&2
    docker compose -f docker-compose.test.yml logs db-test >&2
    exit 1
  fi
done

export SPRING_PROFILES_ACTIVE=test
export TEST_DB_URL="jdbc:postgresql://localhost:5433/tibiastats_test"
export TEST_DB_USERNAME="tibia"
export TEST_DB_PASSWORD="secret"

mvn -U clean test
BASH
chmod +x run-tests.sh

python3 - <<'PY'
from pathlib import Path

world = Path('src/main/java/com/nathan/tibiastats/application/scheduler/WorldScrapeScheduler.java')
if world.exists():
    text = world.read_text()
    if 'ConditionalOnProperty' not in text:
        text = text.replace('import org.springframework.scheduling.annotation.Scheduled;\n', 'import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;\nimport org.springframework.scheduling.annotation.Scheduled;\n')
        text = text.replace('@Component\npublic class WorldScrapeScheduler', '@Component\n@ConditionalOnProperty(prefix = "tibiastats.scrape.worlds", name = "enabled", havingValue = "true", matchIfMissing = true)\npublic class WorldScrapeScheduler')
        world.write_text(text)

# Some project versions have this scheduler under application/scheduler; newer ones may have it elsewhere.
for path in Path('src/main/java').rglob('*CharacterDetails*Scheduler*.java'):
    text = path.read_text()
    if '@Component' in text and 'ConditionalOnProperty' not in text:
        text = text.replace('import org.springframework.scheduling.annotation.Scheduled;\n', 'import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;\nimport org.springframework.scheduling.annotation.Scheduled;\n')
        text = text.replace('@Component\npublic class', '@Component\n@ConditionalOnProperty(prefix = "tibiastats.scrape.character-details", name = "enabled", havingValue = "true", matchIfMissing = true)\npublic class')
        path.write_text(text)

world_test = Path('src/test/java/com/nathan/tibiastats/controller/WorldControllerTest.java')
if world_test.exists():
    text = world_test.read_text()
    if 'import com.nathan.tibiastats.AbstractPostgresTest;' not in text:
        text = text.replace('package com.nathan.tibiastats.controller;\n\n', 'package com.nathan.tibiastats.controller;\n\nimport com.nathan.tibiastats.AbstractPostgresTest;\n')
    text = text.replace('class WorldControllerTest {', 'class WorldControllerTest extends AbstractPostgresTest {')
    text = text.replace('new World("Antica","Open PvP","Europe")', 'new World("Testica","Open PvP","Europe")')
    text = text.replace('/api/online/worlds/Antica', '/api/online/worlds/Testica')
    world_test.write_text(text)

security_test = Path('src/test/java/com/nathan/tibiastats/security/SecurityIntegrationTest.java')
if security_test.exists():
    text = security_test.read_text()
    if 'import com.nathan.tibiastats.AbstractPostgresTest;' not in text:
        text = text.replace('package com.nathan.tibiastats.security;\n\n', 'package com.nathan.tibiastats.security;\n\nimport com.nathan.tibiastats.AbstractPostgresTest;\n')
    text = text.replace('class SecurityIntegrationTest {', 'class SecurityIntegrationTest extends AbstractPostgresTest {')
    security_test.write_text(text)
PY

echo "Test infrastructure patch applied. Run: ./run-tests.sh"
