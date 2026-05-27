ALTER TABLE character_statrecords
    ADD COLUMN IF NOT EXISTS vocation_filter_id INTEGER NOT NULL DEFAULT 0;

UPDATE character_statrecords
SET vocation_filter_id = 0
WHERE vocation_filter_id IS NULL;

ALTER TABLE character_statrecords
    ALTER COLUMN vocation_filter_id SET DEFAULT 0;

ALTER TABLE character_statrecords
    ALTER COLUMN vocation_filter_id SET NOT NULL;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT c.conname
    INTO constraint_name
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    WHERE t.relname = 'character_statrecords'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) ILIKE '%category%'
    LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE character_statrecords DROP CONSTRAINT ' || quote_ident(constraint_name);
    END IF;
END $$;

ALTER TABLE character_statrecords
    ADD CONSTRAINT chk_character_statrecords_category CHECK (
        category IN (
            'ACHIEVEMENTS',
            'AXE_FIGHTING',
            'BOSS_POINTS',
            'BOUNTY_POINTS_EARNED',
            'CHARM_POINTS',
            'CLUB_FIGHTING',
            'DISTANCE_FIGHTING',
            'DROME_SCORE',
            'EXPERIENCE',
            'FISHING',
            'FIST_FIGHTING',
            'GOSHNARS_TAINT',
            'LOYALTY_POINTS',
            'MAGIC_LEVEL',
            'SHIELDING',
            'SWORD_FIGHTING',
            'WEEKLY_TASKS_COMPLETED'
        )
    );

WITH duplicated AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY character_id, world_id, category, vocation_filter_id, date
            ORDER BY scraped_at DESC, id DESC
        ) AS rn
    FROM character_statrecords
)
DELETE FROM character_statrecords csr
USING duplicated d
WHERE csr.id = d.id
  AND d.rn > 1;

DROP INDEX IF EXISTS ux_character_statrecords_daily_scope;
DROP INDEX IF EXISTS ux_character_statrecords_daily;

CREATE UNIQUE INDEX IF NOT EXISTS ux_character_statrecords_daily_scope
ON character_statrecords(character_id, world_id, category, vocation_filter_id, date);

CREATE INDEX IF NOT EXISTS idx_csr_world_cat_voc_date
ON character_statrecords(world_id, category, vocation_filter_id, date);

CREATE TABLE IF NOT EXISTS highscore_scrape_scopes (
    id BIGSERIAL PRIMARY KEY,
    world_id INTEGER NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    world_name TEXT NOT NULL,
    category TEXT NOT NULL,
    vocation_filter_id INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_started_at TIMESTAMP WITH TIME ZONE,
    last_finished_at TIMESTAMP WITH TIME ZONE,
    last_scraped_at TIMESTAMP WITH TIME ZONE,
    last_status TEXT,
    last_page_count INTEGER,
    last_row_count INTEGER,
    last_duration_ms BIGINT,
    last_error TEXT,
    UNIQUE(world_id, category, vocation_filter_id)
);

CREATE INDEX IF NOT EXISTS idx_highscore_scopes_next
ON highscore_scrape_scopes(last_scraped_at NULLS FIRST, last_finished_at NULLS FIRST, world_name, category, vocation_filter_id);
