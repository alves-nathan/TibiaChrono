#!/usr/bin/env bash
set -euo pipefail

if [ ! -f "pom.xml" ] || [ ! -d "src/test/java/com/nathan/tibiastats" ]; then
  echo "ERROR: run this patch from the TibiaChrono project root." >&2
  exit 1
fi

TEST_FILE="src/test/java/com/nathan/tibiastats/api/CharacterOnlineActivityIntegrationTest.java"
BASE_TEST_FILE="src/test/java/com/nathan/tibiastats/AbstractPostgresTest.java"

if [ ! -f "$TEST_FILE" ]; then
  echo "ERROR: expected test file not found: $TEST_FILE" >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path

path = Path("src/test/java/com/nathan/tibiastats/api/CharacterOnlineActivityIntegrationTest.java")
text = path.read_text()
replacements = {
    'get("/api/characters/Knight%20Sample/online-history")': 'get("/api/characters/{name}/online-history", "Knight Sample")',
    'get("/api/characters/Knight%20Sample/online-sessions")': 'get("/api/characters/{name}/online-sessions", "Knight Sample")',
    'get("/api/characters/Knight%20Sample/activity-summary")': 'get("/api/characters/{name}/activity-summary", "Knight Sample")',
    'get("/api/characters/Unknown/online-history")': 'get("/api/characters/{name}/online-history", "Unknown")',
}
changed = False
for old, new in replacements.items():
    if old in text:
        text = text.replace(old, new)
        changed = True
path.write_text(text)

base = Path("src/test/java/com/nathan/tibiastats/AbstractPostgresTest.java")
if base.exists():
    text = base.read_text()
    marker = "truncate table\n                    "
    table = "highscore_http_backoff_state,\n                    "
    if "highscore_http_backoff_state" not in text:
        if marker not in text:
            raise SystemExit("ERROR: could not locate truncate table block in AbstractPostgresTest.java")
        text = text.replace(marker, marker + table, 1)
        base.write_text(text)
        changed = True

if not changed:
    print("Patch already applied or no changes were necessary.")
PY

echo "Patch applied: CharacterOnlineActivityIntegrationTest now uses MockMvc URI templates instead of pre-encoded %20 paths."
echo "Also ensured AbstractPostgresTest clears highscore_http_backoff_state for better test isolation."
echo "Run: make test"
echo "Then: make test-coverage"
