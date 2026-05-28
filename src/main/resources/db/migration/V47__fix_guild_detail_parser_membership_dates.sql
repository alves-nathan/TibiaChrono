-- Store the official Tibia guild joining date shown on the guild member table.
-- joined_at remains an instant used by the API/history model; when Tibia exposes
-- Joining Date, it is normalized to midnight UTC. first_seen_at still records
-- when this application first observed the membership.

ALTER TABLE guild_memberships
    ADD COLUMN IF NOT EXISTS joined_on DATE;

CREATE INDEX IF NOT EXISTS idx_guild_memberships_joined_on
    ON guild_memberships (guild_id, joined_on DESC, character_name_snapshot);
