#!/usr/bin/env bash
set -euo pipefail

MIGRATION_DIR="src/main/resources/db/migration"
OLD_FILE="$MIGRATION_DIR/V5__highscore_compact_storage.sql"

if [ ! -d "$MIGRATION_DIR" ]; then
  echo "Migration directory not found: $MIGRATION_DIR" >&2
  echo "Run this script from the project root, e.g. ~/TibiaChrono" >&2
  exit 1
fi

if [ ! -f "$OLD_FILE" ]; then
  echo "Nothing to rename: $OLD_FILE was not found."
  echo "Current highscore compact migration candidates:"
  ls "$MIGRATION_DIR"/*highscore_compact_storage.sql 2>/dev/null || true
  exit 0
fi

# Pick the next free Flyway integer version to avoid conflicts with existing migrations.
max_version=0
while IFS= read -r file; do
  base="$(basename "$file")"
  if [[ "$base" =~ ^V([0-9]+)__.*\.sql$ ]]; then
    version="${BASH_REMATCH[1]}"
    if (( version > max_version )); then
      max_version="$version"
    fi
  fi
done < <(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' | sort)

new_version=$((max_version + 1))
NEW_FILE="$MIGRATION_DIR/V${new_version}__highscore_compact_storage.sql"

# Extra guard in case max+1 exists due to unusual filenames or race.
while [ -e "$NEW_FILE" ]; do
  new_version=$((new_version + 1))
  NEW_FILE="$MIGRATION_DIR/V${new_version}__highscore_compact_storage.sql"
done

mv "$OLD_FILE" "$NEW_FILE"

echo "Renamed Flyway migration:"
echo "  $OLD_FILE"
echo "-> $NEW_FILE"

echo
echo "Current migrations:"
ls "$MIGRATION_DIR"/V*__*.sql | sed 's#^#  #' | sort
