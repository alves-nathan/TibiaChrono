#!/usr/bin/env bash
set -Eeuo pipefail

TARGET="src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java"
BACKUP_SUFFIX=".bak-character-timeline-nullable-timestamp-filters-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

if [[ ! -f "$TARGET" ]]; then
  echo "ERROR: target file not found: $TARGET" >&2
  echo "This patch expects the Character Timeline feature to be present." >&2
  exit 1
fi

cp "$TARGET" "$TARGET$BACKUP_SUFFIX"

python3 - <<'PY'
from pathlib import Path
import re
import sys

path = Path("src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java")
text = path.read_text()
original = text

# PostgreSQL cannot infer the type of a NULL parameter when it is used as
# `:from is null` / `:to is null`. Cast only the NULL-check side; the comparison
# side keeps using the column type naturally.
replacements = [
    (
        r"\(\s*:from\s+is\s+null",
        "(cast(:from as timestamp with time zone) is null",
    ),
    (
        r"\(\s*:to\s+is\s+null",
        "(cast(:to as timestamp with time zone) is null",
    ),
]

for pattern, replacement in replacements:
    text = re.sub(pattern, replacement, text, flags=re.IGNORECASE)

# Idempotence / validation: if the file already had the fix, no change is fine.
already_fixed = "cast(:from as timestamp with time zone) is null" in original or "cast(:to as timestamp with time zone) is null" in original
changed = text != original

if not changed and not already_fixed:
    print("ERROR: no nullable timestamp filters were found to update in CharacterTimelineService.java", file=sys.stderr)
    print("Looked for SQL fragments like ':from is null' and ':to is null'.", file=sys.stderr)
    sys.exit(1)

path.write_text(text)

if changed:
    print("Updated nullable from/to timestamp checks in CharacterTimelineService.java")
else:
    print("CharacterTimelineService.java already appears to be fixed")
PY

cat <<'MSG'

Done.
Next steps:
  make test

If tests pass, optionally run:
  make test-coverage
MSG
