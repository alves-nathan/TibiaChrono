-- Rotation/fairness metadata for the character details scraper.
-- The scheduler must not keep selecting the first N characters forever when a field
-- such as creation_date stays NULL because Tibia.com does not expose it or the parser
-- cannot read it yet.

ALTER TABLE characters
    ADD COLUMN IF NOT EXISTS details_last_scraped_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS details_last_scrape_status TEXT,
    ADD COLUMN IF NOT EXISTS details_last_scrape_error TEXT;

CREATE INDEX IF NOT EXISTS idx_characters_details_last_scraped_at
    ON characters (details_last_scraped_at, id);

CREATE INDEX IF NOT EXISTS idx_character_names_active_character
    ON character_names (active, character_id);
