-- Character name identity rules
--
-- A former name is a safe identity alias for 6 months after rename, but after that
-- CipSoft may release the name for another character. Therefore character_names.name
-- cannot stay globally unique forever. Only one active owner per name is enforced.

ALTER TABLE character_names
    DROP CONSTRAINT IF EXISTS character_names_name_key;

UPDATE character_names
   SET active = false,
       inactive_date = COALESCE(inactive_date, now())
 WHERE active IS NULL;

-- Remove duplicate rows for the same character/name pair before creating the unique index.
DELETE FROM character_names newer
USING character_names older
WHERE newer.id > older.id
  AND newer.character_id = older.character_id
  AND lower(newer.name) = lower(older.name);

-- Safety guard: if duplicated active names exist, keep only the newest active owner and
-- turn the others into inactive historical names so the partial unique index can be created.
WITH ranked_active_names AS (
    SELECT id,
           row_number() OVER (PARTITION BY lower(name) ORDER BY id DESC) AS rn
      FROM character_names
     WHERE active IS TRUE
)
UPDATE character_names cn
   SET active = false,
       inactive_date = COALESCE(inactive_date, now())
  FROM ranked_active_names ranked
 WHERE cn.id = ranked.id
   AND ranked.rn > 1;

CREATE INDEX IF NOT EXISTS idx_character_names_name_lower
    ON character_names (lower(name));

CREATE INDEX IF NOT EXISTS idx_character_names_resolution
    ON character_names (lower(name), active, inactive_date);

CREATE UNIQUE INDEX IF NOT EXISTS ux_character_names_active_name_lower
    ON character_names (lower(name))
    WHERE active IS TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS ux_character_names_character_name_lower
    ON character_names (character_id, lower(name));
