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
