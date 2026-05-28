-- Guild scraper and membership-history model.
--
-- Guild membership changes cannot be known at the exact instant they happen on Tibia.
-- These tables store the moment the application observed the transition during scraping.

ALTER TABLE guilds ADD COLUMN IF NOT EXISTS normalized_name TEXT;
ALTER TABLE guilds ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE guilds ADD COLUMN IF NOT EXISTS homepage TEXT;
ALTER TABLE guilds ADD COLUMN IF NOT EXISTS logo_url TEXT;
ALTER TABLE guilds ADD COLUMN IF NOT EXISTS founded_at DATE;
ALTER TABLE guilds ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE guilds ADD COLUMN IF NOT EXISTS disband_condition TEXT;
ALTER TABLE guilds ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE guilds ADD COLUMN IF NOT EXISTS last_scraped_at TIMESTAMP WITH TIME ZONE;

UPDATE guilds
   SET normalized_name = lower(trim(name))
 WHERE normalized_name IS NULL
   AND name IS NOT NULL;

UPDATE guilds
   SET active = TRUE
 WHERE active IS NULL;

ALTER TABLE guilds ALTER COLUMN normalized_name SET NOT NULL;
ALTER TABLE guilds ALTER COLUMN active SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_guilds_normalized_name
    ON guilds (normalized_name);

CREATE INDEX IF NOT EXISTS idx_guilds_world_active
    ON guilds (world_id, active, last_scraped_at);

CREATE TABLE IF NOT EXISTS guild_snapshots (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL REFERENCES guilds (id) ON DELETE CASCADE,
    scraped_at TIMESTAMP WITH TIME ZONE NOT NULL,
    member_count INTEGER,
    online_count INTEGER,
    raw_hash TEXT
);

CREATE INDEX IF NOT EXISTS idx_guild_snapshots_guild_scraped_at
    ON guild_snapshots (guild_id, scraped_at DESC);

CREATE TABLE IF NOT EXISTS guild_memberships (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL REFERENCES guilds (id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    character_name_snapshot TEXT NOT NULL,
    rank_name TEXT,
    title TEXT,
    vocation TEXT,
    level INTEGER,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    left_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_guild_memberships_guild_active
    ON guild_memberships (guild_id, active, rank_name, character_name_snapshot);

CREATE INDEX IF NOT EXISTS idx_guild_memberships_character_period
    ON guild_memberships (character_id, joined_at DESC, left_at DESC NULLS FIRST);

CREATE UNIQUE INDEX IF NOT EXISTS uq_guild_memberships_one_active_character
    ON guild_memberships (character_id)
    WHERE active IS TRUE;

CREATE TABLE IF NOT EXISTS guild_membership_events (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    character_name_snapshot TEXT NOT NULL,
    event_type TEXT NOT NULL CHECK (event_type IN ('JOINED', 'LEFT', 'TRANSFERRED')),
    from_guild_id BIGINT REFERENCES guilds (id) ON DELETE SET NULL,
    to_guild_id BIGINT REFERENCES guilds (id) ON DELETE SET NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    description TEXT
);

CREATE INDEX IF NOT EXISTS idx_guild_membership_events_character_observed
    ON guild_membership_events (character_id, observed_at DESC);

CREATE INDEX IF NOT EXISTS idx_guild_membership_events_guilds_observed
    ON guild_membership_events (from_guild_id, to_guild_id, observed_at DESC);

CREATE TABLE IF NOT EXISTS guild_invites (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL REFERENCES guilds (id) ON DELETE CASCADE,
    character_name TEXT NOT NULL,
    invited_at DATE,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_guild_invites_guild_active
    ON guild_invites (guild_id, active, character_name);
