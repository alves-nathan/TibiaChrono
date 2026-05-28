#!/usr/bin/env bash
set -euo pipefail

FILE="src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java"

if [[ ! -f "$FILE" ]]; then
  echo "ERROR: $FILE not found. Run this script from the project root." >&2
  exit 1
fi

if grep -q '^import java.time.LocalDate;' "$FILE"; then
  echo "LocalDate import already present in $FILE"
  exit 0
fi

if grep -q '^import java.time.Instant;' "$FILE"; then
  sed -i '/^import java.time.Instant;/a import java.time.LocalDate;' "$FILE"
else
  # Add after package declaration/import block start, keeping it simple and safe.
  awk '
    /^package / { print; print ""; print "import java.time.LocalDate;"; next }
    { print }
  ' "$FILE" > "$FILE.tmp"
  mv "$FILE.tmp" "$FILE"
fi

echo "Fixed: added import java.time.LocalDate; to $FILE"
