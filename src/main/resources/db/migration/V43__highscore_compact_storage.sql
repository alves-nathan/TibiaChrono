-- Production highscore storage model.
--
-- EXP is intentionally stored as a daily snapshot because EXP deltas are the core analytics use case.
-- The same character may appear in the overall EXP ranking and in its vocation ranking on the same day,
-- so highscore_exp_daily is deduplicated by (date, character_id, world_id).
--
-- Non-EXP categories are stored as current state + periods to avoid repeating identical snapshots forever.

CREATE TABLE IF NOT EXISTS highscore_exp_daily (
    date DATE NOT NULL,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    level INTEGER,
    experience BIGINT NOT NULL,
    vocation_id SMALLINT,
    guild_id BIGINT REFERENCES guilds(id) ON DELETE SET NULL,
    first_seen_filter SMALLINT,
    scraped_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (date, character_id, world_id)
);

CREATE INDEX IF NOT EXISTS idx_highscore_exp_daily_world_date_exp
ON highscore_exp_daily(world_id, date, experience DESC);

CREATE INDEX IF NOT EXISTS idx_highscore_exp_daily_character_world_date
ON highscore_exp_daily(character_id, world_id, date);

CREATE INDEX IF NOT EXISTS idx_highscore_exp_daily_guild_date_exp
ON highscore_exp_daily(guild_id, date, experience DESC)
WHERE guild_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS highscore_exp_rank_daily (
    date DATE NOT NULL,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    vocation_filter_id SMALLINT NOT NULL DEFAULT 0,
    rank INTEGER NOT NULL,
    scraped_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (date, character_id, world_id, vocation_filter_id)
);

CREATE INDEX IF NOT EXISTS idx_highscore_exp_rank_daily_scope_rank
ON highscore_exp_rank_daily(world_id, vocation_filter_id, date, rank);

CREATE TABLE IF NOT EXISTS highscore_current_records (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    category SMALLINT NOT NULL,
    vocation_filter_id SMALLINT NOT NULL DEFAULT 0,
    rank INTEGER NOT NULL,
    value BIGINT NOT NULL,
    first_seen_date DATE NOT NULL,
    last_seen_date DATE NOT NULL,
    last_changed_date DATE NOT NULL,
    scraped_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_highscore_current_records_scope UNIQUE(character_id, world_id, category, vocation_filter_id),
    CONSTRAINT chk_highscore_current_records_category CHECK (category BETWEEN 1 AND 17)
);

CREATE INDEX IF NOT EXISTS idx_highscore_current_records_scope_rank
ON highscore_current_records(world_id, category, vocation_filter_id, rank);

CREATE INDEX IF NOT EXISTS idx_highscore_current_records_character_scope
ON highscore_current_records(character_id, world_id, category, vocation_filter_id);

CREATE TABLE IF NOT EXISTS highscore_record_periods (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    category SMALLINT NOT NULL,
    vocation_filter_id SMALLINT NOT NULL DEFAULT 0,
    rank INTEGER NOT NULL,
    value BIGINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_highscore_record_periods_category CHECK (category BETWEEN 1 AND 17),
    CONSTRAINT chk_highscore_record_periods_valid_range CHECK (valid_until IS NULL OR valid_until > valid_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_highscore_record_periods_open
ON highscore_record_periods(character_id, world_id, category, vocation_filter_id)
WHERE valid_until IS NULL;

CREATE INDEX IF NOT EXISTS idx_highscore_record_periods_character_date
ON highscore_record_periods(character_id, world_id, category, vocation_filter_id, valid_from, valid_until);

CREATE INDEX IF NOT EXISTS idx_highscore_record_periods_scope_date_rank
ON highscore_record_periods(world_id, category, vocation_filter_id, valid_from, valid_until, rank);

-- Optional cleanup helper for future maintenance. It is intentionally not run automatically here:
-- character_statrecords remains as legacy/staging data until you decide to compact or purge it.
