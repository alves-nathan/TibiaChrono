-- Tibia added new highscore categories. Keep the Java enum, GraphQL enum,
-- parser mapping and database CHECK constraint aligned.

DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'character_statrecords'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%category%'
    LOOP
        EXECUTE format('ALTER TABLE character_statrecords DROP CONSTRAINT IF EXISTS %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE character_statrecords
ADD CONSTRAINT character_statrecords_category_check
CHECK (
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
