#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="${1:-$(pwd)}"
MIGRATION_DIR="$PROJECT_DIR/src/main/resources/db/migration"

if [ ! -d "$MIGRATION_DIR" ]; then
  echo "Migration directory not found: $MIGRATION_DIR" >&2
  exit 1
fi

if find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__fix_bigint_foreign_keys.sql' | grep -q .; then
  echo "Bigint foreign-key migration already exists. Nothing to do."
  find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__fix_bigint_foreign_keys.sql' -print
  exit 0
fi

MAX_VERSION=$(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' \
  | sed -E 's|.*/V([0-9]+)__.*|\1|' \
  | sort -n \
  | tail -1)

if [ -z "${MAX_VERSION:-}" ]; then
  echo "Could not detect latest Flyway migration version in $MIGRATION_DIR" >&2
  exit 1
fi

NEXT_VERSION=$((MAX_VERSION + 1))
MIGRATION_FILE="$MIGRATION_DIR/V${NEXT_VERSION}__fix_bigint_foreign_keys.sql"

cat > "$MIGRATION_FILE" <<'SQL'
-- Align FK column types with entity identifier types.
-- Several tables were created with BIGSERIAL primary keys but INTEGER foreign keys.
-- Hibernate validates Long entity identifiers as BIGINT, so these INTEGER FKs fail schema validation.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'character_names'
          AND column_name = 'character_id'
          AND data_type <> 'bigint'
    ) THEN
        ALTER TABLE public.character_names
            ALTER COLUMN character_id TYPE BIGINT
            USING character_id::bigint;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'scrape_players'
          AND column_name = 'scrape_id'
          AND data_type <> 'bigint'
    ) THEN
        ALTER TABLE public.scrape_players
            ALTER COLUMN scrape_id TYPE BIGINT
            USING scrape_id::bigint;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'scrape_players'
          AND column_name = 'character_id'
          AND data_type <> 'bigint'
    ) THEN
        ALTER TABLE public.scrape_players
            ALTER COLUMN character_id TYPE BIGINT
            USING character_id::bigint;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'character_worlds'
          AND column_name = 'character_id'
          AND data_type <> 'bigint'
    ) THEN
        ALTER TABLE public.character_worlds
            ALTER COLUMN character_id TYPE BIGINT
            USING character_id::bigint;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'character_deaths'
          AND column_name = 'character_id'
          AND data_type <> 'bigint'
    ) THEN
        ALTER TABLE public.character_deaths
            ALTER COLUMN character_id TYPE BIGINT
            USING character_id::bigint;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'guild_characters'
          AND column_name = 'guild_id'
          AND data_type <> 'bigint'
    ) THEN
        ALTER TABLE public.guild_characters
            ALTER COLUMN guild_id TYPE BIGINT
            USING guild_id::bigint;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'guild_characters'
          AND column_name = 'character_id'
          AND data_type <> 'bigint'
    ) THEN
        ALTER TABLE public.guild_characters
            ALTER COLUMN character_id TYPE BIGINT
            USING character_id::bigint;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'character_statrecords'
          AND column_name = 'character_id'
          AND data_type <> 'bigint'
    ) THEN
        ALTER TABLE public.character_statrecords
            ALTER COLUMN character_id TYPE BIGINT
            USING character_id::bigint;
    END IF;
END $$;
SQL

echo "Created migration: $MIGRATION_FILE"
echo "Next step: ./run-tests.sh"
