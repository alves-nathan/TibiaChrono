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
