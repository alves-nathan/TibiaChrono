ALTER TABLE character_statrecords
    DROP CONSTRAINT IF EXISTS character_statrecords_category_check;

ALTER TABLE character_statrecords
    ADD CONSTRAINT character_statrecords_category_check CHECK (
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
