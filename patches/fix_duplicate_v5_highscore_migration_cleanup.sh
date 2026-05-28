#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

OLD_NAME="V5__highscore_compact_storage.sql"
MIGRATION_NAME="highscore_compact_storage.sql"
SRC_DIR="src/main/resources/db/migration"
TARGET_DIR="target/classes/db/migration"

if [[ ! -d "$SRC_DIR" ]]; then
  echo "[ERROR] Migration directory not found: $SRC_DIR"
  echo "Run this script from the project root, e.g.: cd ~/TibiaChrono && bash ../patches/fix_duplicate_v5_highscore_migration_cleanup.sh"
  exit 1
fi

choose_version() {
  local start=${1:-50}
  local version=$start
  while true; do
    if ! find "$SRC_DIR" "$TARGET_DIR" -maxdepth 1 -type f -name "V${version}__*.sql" 2>/dev/null | grep -q .; then
      echo "$version"
      return 0
    fi
    version=$((version + 1))
  done
}

NEW_VERSION="$(choose_version 50)"
NEW_NAME="V${NEW_VERSION}__${MIGRATION_NAME}"

# If the source still has the duplicate V5 migration, rename it to a safe high version.
if [[ -f "$SRC_DIR/$OLD_NAME" ]]; then
  if [[ -f "$SRC_DIR/$NEW_NAME" ]]; then
    echo "[INFO] $SRC_DIR/$NEW_NAME already exists. Removing duplicate source $SRC_DIR/$OLD_NAME"
    rm -f "$SRC_DIR/$OLD_NAME"
  else
    echo "[INFO] Renaming source migration: $SRC_DIR/$OLD_NAME -> $SRC_DIR/$NEW_NAME"
    mv "$SRC_DIR/$OLD_NAME" "$SRC_DIR/$NEW_NAME"
  fi
else
  echo "[INFO] Source duplicate not found: $SRC_DIR/$OLD_NAME"
fi

# Remove stale copied resources from target/classes. Spring Boot devtools may still see these.
if [[ -f "$TARGET_DIR/$OLD_NAME" ]]; then
  echo "[INFO] Removing stale copied migration: $TARGET_DIR/$OLD_NAME"
  rm -f "$TARGET_DIR/$OLD_NAME"
else
  echo "[INFO] Stale target duplicate not found: $TARGET_DIR/$OLD_NAME"
fi

# Also remove duplicated files with the same old name in common build dirs.
find target build out -path "*/db/migration/$OLD_NAME" -type f 2>/dev/null -print -delete || true

# Copy/ensure the renamed migration exists in target/classes when target/classes exists.
# In dev mode, target/classes can be used directly by Spring Boot.
if [[ -d "$TARGET_DIR" ]]; then
  RENAMED_SRC="$(find "$SRC_DIR" -maxdepth 1 -type f -name "V*__${MIGRATION_NAME}" | sort -V | tail -n 1 || true)"
  if [[ -n "$RENAMED_SRC" ]]; then
    mkdir -p "$TARGET_DIR"
    cp "$RENAMED_SRC" "$TARGET_DIR/$(basename "$RENAMED_SRC")"
    echo "[INFO] Ensured renamed migration is present in target/classes: $TARGET_DIR/$(basename "$RENAMED_SRC")"
  fi
fi

echo "[INFO] Current highscore compact storage migration files:"
find "$SRC_DIR" "$TARGET_DIR" -maxdepth 1 -type f -name "V*__${MIGRATION_NAME}" 2>/dev/null | sort -V || true

echo "[INFO] Current V5 migration files:"
find "$SRC_DIR" "$TARGET_DIR" -maxdepth 1 -type f -name "V5__*.sql" 2>/dev/null | sort || true

echo "[OK] Duplicate V5 cleanup complete. Restart the app now."
