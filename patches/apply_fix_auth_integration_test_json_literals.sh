#!/usr/bin/env bash
set -euo pipefail

TEST_FILE="src/test/java/com/nathan/tibiastats/auth/AuthIntegrationTest.java"

if [ ! -f "pom.xml" ] || [ ! -d "src/test/java/com/nathan/tibiastats" ]; then
  echo "ERROR: run this script from the TibiaChrono project root." >&2
  exit 1
fi

if [ ! -f "$TEST_FILE" ]; then
  echo "ERROR: $TEST_FILE was not found." >&2
  exit 1
fi

BACKUP_FILE="$TEST_FILE.bak.$(date +%Y%m%d%H%M%S)"
cp "$TEST_FILE" "$BACKUP_FILE"

cat > "$TEST_FILE" <<'JAVA'
package com.nathan.tibiastats.auth;

import com.jayway.jsonpath.JsonPath;
import com.nathan.tibiastats.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractPostgresTest {
    @Autowired
    MockMvc mvc;

    @Test
    void register_login_refresh_logout_blacklist() throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"tester","password":"secret"}
                                """))
                .andExpect(status().isOk());

        var login = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"tester","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyString())))
                .andReturn();

        String refresh = JsonPath.read(login.getResponse().getContentAsString(), "$.refreshToken");

        var refreshed = mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.refreshToken", not(emptyString())))
                .andReturn();

        String access = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }
}
JAVA

python3 - <<'PY'
from pathlib import Path
text = Path('src/test/java/com/nathan/tibiastats/auth/AuthIntegrationTest.java').read_text()
required = [
    'content("""',
    '{"username":"tester","password":"secret"}',
    '{"refreshToken":"%s"}',
    '.formatted(refresh)',
]
missing = [item for item in required if item not in text]
if missing:
    raise SystemExit('ERROR: AuthIntegrationTest rewrite verification failed. Missing: ' + ', '.join(missing))
print('AuthIntegrationTest rewritten with Java text block JSON payloads.')
PY

cat <<EOF
Patch applied successfully.
Backup created at: $BACKUP_FILE

Next steps:
  make test
EOF
