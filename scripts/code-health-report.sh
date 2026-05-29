#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="${1:-.}"
LIMIT="${LIMIT:-25}"
WARN_LOC="${WARN_LOC:-400}"
FAIL_LOC="${FAIL_LOC:-0}"

if [[ ! -d "$ROOT/src/main/java" ]]; then
  echo "ERROR: $ROOT does not look like a TibiaChrono project root." >&2
  exit 1
fi

cd "$ROOT"

echo "# TibiaChrono code health report"
echo
echo "Generated at: $(date -Iseconds)"
echo
echo "## Java source totals"

total_files=$(find src/main/java -name '*.java' -type f | wc -l | tr -d ' ')
total_loc=$(find src/main/java -name '*.java' -type f -print0 | xargs -0 awk 'END { print NR }')
printf -- '- Production Java files: %s\n' "$total_files"
printf -- '- Production Java LOC: %s\n' "$total_loc"
echo

echo "## Largest production Java files"
find src/main/java -name '*.java' -type f -print0 \
  | xargs -0 wc -l \
  | sort -nr \
  | awk -v limit="$LIMIT" '$2 != "total" && ++shown <= limit { printf "- %5d %s\n", $1, $2 }'
echo

echo "## Files above warning threshold (${WARN_LOC} LOC)"
above_warn=$(find src/main/java -name '*.java' -type f -print0 \
  | xargs -0 wc -l \
  | awk -v threshold="$WARN_LOC" '$1 >= threshold && $2 != "total" { printf "- %5d %s\n", $1, $2 }')
if [[ -n "$above_warn" ]]; then
  printf '%s\n' "$above_warn"
else
  echo "- none"
fi
echo

echo "## Package boundary hotspots"
printf -- '- application.service Java files: '
find src/main/java/com/nathan/tibiastats/application/service -name '*.java' -type f 2>/dev/null | wc -l | tr -d ' '
printf -- '- application.query Java files: '
find src/main/java/com/nathan/tibiastats/application/query -name '*.java' -type f 2>/dev/null | wc -l | tr -d ' '
printf -- '- scraper adapter Java files: '
find src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper -name '*.java' -type f 2>/dev/null | wc -l | tr -d ' '
echo

echo "## Direct JDBC usage outside application.query/infrastructure"
jdbc_usage=$(grep -R "org\.springframework\.jdbc" -n src/main/java 2>/dev/null \
  | grep -v '/application/query/' \
  | grep -v '/infrastructure/' || true)
if [[ -n "$jdbc_usage" ]]; then
  printf '%s\n' "$jdbc_usage"
else
  echo "- none"
fi
echo

echo "## Local artifact hygiene"
artifacts=$(find . \
  -path './.git' -prune -o \
  -path './.idea' -print -prune -o \
  -path './.test-maven' -print -prune -o \
  -path './target' -print -prune -o \
  -path './patches/.backups' -print -prune -o \
  \( -name '*.bak' -o -name '*.bak-*' -o -name '*.bak.*' -o -name '*Zone.Identifier*' \) -print | sort)
if [[ -n "$artifacts" ]]; then
  printf '%s\n' "$artifacts"
else
  echo "- none"
fi

if [[ "$FAIL_LOC" =~ ^[0-9]+$ && "$FAIL_LOC" -gt 0 ]]; then
  offenders=$(find src/main/java -name '*.java' -type f -print0 \
    | xargs -0 wc -l \
    | awk -v threshold="$FAIL_LOC" '$1 >= threshold && $2 != "total" { print $2 }')
  if [[ -n "$offenders" ]]; then
    echo
    echo "ERROR: files above FAIL_LOC=${FAIL_LOC}:" >&2
    printf '%s\n' "$offenders" >&2
    exit 1
  fi
fi
