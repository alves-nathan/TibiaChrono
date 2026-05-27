-- Store highscores by the ranking scope used on tibia.com.
-- world_id already identifies the world. vocation_filter_id identifies the profession filter:
-- 0 = all vocations/general; other values follow the tibia.com highscores profession parameter.

ALTER TABLE character_statrecords
    ADD COLUMN IF NOT EXISTS vocation_filter_id INTEGER NOT NULL DEFAULT 0;

UPDATE character_statrecords
SET vocation_filter_id = 0
WHERE vocation_filter_id IS NULL;

DROP INDEX IF EXISTS ux_character_statrecords_daily_identity;

DELETE FROM character_statrecords older
USING character_statrecords newer
WHERE older.character_id = newer.character_id
  AND older.world_id = newer.world_id
  AND older.category = newer.category
  AND older.vocation_filter_id = newer.vocation_filter_id
  AND older.date = newer.date
  AND older.id < newer.id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_character_statrecords_daily_scope
    ON character_statrecords(character_id, world_id, category, vocation_filter_id, date);

CREATE INDEX IF NOT EXISTS idx_csr_world_cat_voc_date_rank
    ON character_statrecords(world_id, category, vocation_filter_id, date, rank);

CREATE INDEX IF NOT EXISTS idx_csr_char_cat_voc_date
    ON character_statrecords(character_id, category, vocation_filter_id, date);
