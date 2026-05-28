#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${1:-$(pwd)}"
MIGRATION_DIR="$PROJECT_DIR/src/main/resources/db/migration"

if [[ ! -d "$MIGRATION_DIR" ]]; then
  echo "Migration directory not found: $MIGRATION_DIR" >&2
  echo "Run this script from the project root, for example: cd ~/TibiaChrono && bash /mnt/data/apply_fix_integer_identity_tables_migration.sh" >&2
  exit 1
fi

# Pick the next integer Flyway version, avoiding collisions with existing V*.sql files.
NEXT_VERSION=$(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' \
  | sed -E 's#^.*/V([0-9]+)__.*#\1#' \
  | sort -n \
  | tail -1)

if [[ -z "${NEXT_VERSION:-}" ]]; then
  NEXT_VERSION=1
else
  NEXT_VERSION=$((NEXT_VERSION + 1))
fi

TARGET="$MIGRATION_DIR/V${NEXT_VERSION}__align_integer_identity_tables.sql"

cat > "$TARGET" <<'SQL'
-- Align dictionary-table identity columns with the JPA model.
--
-- World.id and Vocation.id are mapped as Integer in Java, but the original
-- migration created them as BIGSERIAL/BIGINT. Hibernate schema validation
-- expects INTEGER for these IDs. These tables are small reference/dictionary
-- tables, so INTEGER is the correct database type here.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'worlds'
          AND column_name = 'id'
          AND data_type = 'bigint'
    ) THEN
        IF EXISTS (SELECT 1 FROM worlds WHERE id > 2147483647 OR id < -2147483648) THEN
            RAISE EXCEPTION 'Cannot convert worlds.id from BIGINT to INTEGER: value outside integer range';
        END IF;

        ALTER TABLE worlds ALTER COLUMN id DROP DEFAULT;
        ALTER TABLE worlds ALTER COLUMN id TYPE INTEGER USING id::INTEGER;

        IF EXISTS (
            SELECT 1
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public'
              AND c.relkind = 'S'
              AND c.relname = 'worlds_id_seq'
        ) THEN
            ALTER SEQUENCE worlds_id_seq AS INTEGER;
            ALTER TABLE worlds ALTER COLUMN id SET DEFAULT nextval('worlds_id_seq'::regclass);
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'vocations'
          AND column_name = 'id'
          AND data_type = 'bigint'
    ) THEN
        IF EXISTS (SELECT 1 FROM vocations WHERE id > 2147483647 OR id < -2147483648) THEN
            RAISE EXCEPTION 'Cannot convert vocations.id from BIGINT to INTEGER: value outside integer range';
        END IF;

        ALTER TABLE vocations ALTER COLUMN id DROP DEFAULT;
        ALTER TABLE vocations ALTER COLUMN id TYPE INTEGER USING id::INTEGER;

        IF EXISTS (
            SELECT 1
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public'
              AND c.relkind = 'S'
              AND c.relname = 'vocations_id_seq'
        ) THEN
            ALTER SEQUENCE vocations_id_seq AS INTEGER;
            ALTER TABLE vocations ALTER COLUMN id SET DEFAULT nextval('vocations_id_seq'::regclass);
        END IF;
    END IF;
END $$;
SQL

printf 'Created migration: %s\n' "$TARGET"
printf '\nNow run:\n  ./run-tests.sh\n'
