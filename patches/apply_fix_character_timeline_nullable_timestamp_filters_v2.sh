#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-.}"
cd "$ROOT_DIR"

SERVICE_FILE="src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java"

if [[ ! -f "$SERVICE_FILE" ]]; then
  echo "ERROR: $SERVICE_FILE not found. Run this script from the project root." >&2
  exit 1
fi

BACKUP_FILE="${SERVICE_FILE}.bak.$(date +%Y%m%d%H%M%S)"
cp "$SERVICE_FILE" "$BACKUP_FILE"

python3 - <<'PY'
from pathlib import Path
import re

path = Path("src/main/java/com/nathan/tibiastats/application/service/CharacterTimelineService.java")
text = path.read_text()
original = text

# PostgreSQL cannot infer the SQL type of nullable named parameters used only in
# an IS NULL expression, for example: (:from is null or d.death_date >= :from).
# This patch is intentionally broader than the previous one: it handles parameter
# names such as :from, :to, :fromDate, :toDate, :startAt, :endAt, etc., and it is
# formatting-insensitive inside Java text blocks or string fragments.
TIMESTAMP_PARAM_RE = re.compile(
    r"(?<!cast\():(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s+is\s+null",
    re.IGNORECASE,
)

def looks_like_timestamp_filter(name: str) -> bool:
    lower = name.lower()
    return (
        lower in {"from", "to", "start", "end", "started", "ended"}
        or "from" in lower
        or "to" in lower
        or "start" in lower
        or "end" in lower
        or "date" in lower
        or "time" in lower
        or "at" in lower
    )

changed = []

def replace_nullable_timestamp_param(match: re.Match) -> str:
    name = match.group("name")
    if not looks_like_timestamp_filter(name):
        return match.group(0)
    changed.append(name)
    return f"cast(:{name} as timestamp with time zone) is null"

text = TIMESTAMP_PARAM_RE.sub(replace_nullable_timestamp_param, text)

# Fallback for a common compact pattern where the optional date filter may have
# been written without whitespace around IS NULL. This keeps the patch resilient
# to slightly different formatting in generated features.
COMPACT_RE = re.compile(
    r"(?<!cast\():(?P<name>[A-Za-z_][A-Za-z0-9_]*)(?P<space>\s*)is(?P<space2>\s*)null",
    re.IGNORECASE,
)

def replace_compact(match: re.Match) -> str:
    name = match.group("name")
    if not looks_like_timestamp_filter(name):
        return match.group(0)
    # Avoid double-counting replacements already handled by the first pass.
    if f"cast(:{name} as timestamp with time zone) is null" in text[max(0, match.start()-80):match.start()+120]:
        return match.group(0)
    changed.append(name)
    return f"cast(:{name} as timestamp with time zone) is null"

text = COMPACT_RE.sub(replace_compact, text)

if text == original:
    print("ERROR: no nullable timestamp named parameters were found to update in CharacterTimelineService.java", flush=True)
    print("Searched for patterns like ':from is null', ':fromDate is null', ':startAt is null', etc.", flush=True)
    print("Nearby lines containing 'is null' for manual inspection:", flush=True)
    for i, line in enumerate(original.splitlines(), start=1):
        if "is null" in line.lower():
            print(f"{i}: {line}")
    raise SystemExit(1)

path.write_text(text)
unique = ", ".join(sorted(set(changed), key=str.lower))
print(f"Updated nullable timestamp filters in CharacterTimelineService.java for parameter(s): {unique}")
PY

if command -v git >/dev/null 2>&1; then
  echo
  echo "Changed lines:"
  git --no-pager diff -- "$SERVICE_FILE" || true
fi

echo
echo "Backup created at: $BACKUP_FILE"
echo "Done. Run: make test"
