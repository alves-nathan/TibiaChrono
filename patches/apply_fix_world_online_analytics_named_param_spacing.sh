#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(pwd)"
SERVICE_FILE="$ROOT_DIR/src/main/java/com/nathan/tibiastats/application/service/WorldOnlineAnalyticsService.java"

if [[ ! -f "$ROOT_DIR/pom.xml" || ! -f "$SERVICE_FILE" ]]; then
  echo "Run this script from the TibiaChrono project root."
  exit 1
fi

backup_file() {
  local file="$1"
  local backup="$file.bak.$(date +%Y%m%d%H%M%S)"
  cp "$file" "$backup"
  echo "Backup created: $backup"
}

backup_file "$SERVICE_FILE"

python3 - <<'PY'
from pathlib import Path

path = Path("src/main/java/com/nathan/tibiastats/application/service/WorldOnlineAnalyticsService.java")
text = path.read_text()
original = text

# NamedParameterJdbcTemplate treats ':' followed by Java identifier characters as a named parameter.
# The bucket/compare SQL appends a text block immediately after ':from' or ':to'. Java text blocks do
# not include a leading newline after the opening delimiter, so the generated SQL became ':togroup'.
# Keep each dynamic predicate terminated with whitespace/newline before the next SQL fragment.
replacements = {
    'sql.append(" and s.scrape_time >= :from");': 'sql.append(" and s.scrape_time >= :from\\n");',
    'sql.append(" and s.scrape_time <= :to");': 'sql.append(" and s.scrape_time <= :to\\n");',
}

for old, new in replacements.items():
    if old not in text and new not in text:
        raise SystemExit(f"Expected snippet not found: {old}")
    text = text.replace(old, new)

if text == original:
    print("WorldOnlineAnalyticsService.java already contains the named-parameter spacing fix.")
else:
    path.write_text(text)
    print("Updated WorldOnlineAnalyticsService.java to terminate dynamic :from/:to predicates with newlines.")

# Safety check: no direct dynamic range append should remain without trailing newline.
remaining = [
    'sql.append(" and s.scrape_time >= :from");',
    'sql.append(" and s.scrape_time <= :to");',
]
for snippet in remaining:
    if snippet in path.read_text():
        raise SystemExit(f"Unsafe range append still present: {snippet}")
PY

echo
cat <<'EOF'
Patch applied.

Run:
  make test

If it passes, optionally run:
  make test-coverage
EOF
