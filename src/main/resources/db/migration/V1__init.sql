CREATE TABLE IF NOT EXISTS worlds (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  pvp_type TEXT,
  location TEXT
);

CREATE TABLE IF NOT EXISTS scrapes (
  id BIGSERIAL PRIMARY KEY,
  world_id INTEGER NOT NULL REFERENCES worlds (id) ON DELETE CASCADE,
  scrape_time TIMESTAMP WITH TIME ZONE NOT NULL,
  players_online INTEGER NOT NULL,
  player_list TEXT
);

CREATE TABLE IF NOT EXISTS vocations (
  id BIGSERIAL PRIMARY KEY,
  name TEXT,
  promotion_name TEXT
);

CREATE TABLE IF NOT EXISTS character_names (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  active BOOLEAN
);

CREATE TABLE IF NOT EXISTS characters (
  id BIGSERIAL PRIMARY KEY,
  name_id INTEGER REFERENCES character_names (id),
  sex TEXT CHECK (sex IN ('male', 'female')),
  vocation_id INTEGER REFERENCES vocations (id),
  level INTEGER,
  achievement_points INTEGER,
  residence TEXT,
  last_login TIMESTAMP WITH TIME ZONE,
  acc_status TEXT
);

CREATE TABLE IF NOT EXISTS character_deaths (
  id BIGSERIAL PRIMARY KEY,
  character_id INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
  death_date TIMESTAMP WITH TIME ZONE,
  killed_by TEXT
);

-- Enum stored as TEXT with CHECK constraint
CREATE TABLE IF NOT EXISTS character_statrecords (
  id BIGSERIAL PRIMARY KEY,
  character_id INTEGER NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
  category TEXT NOT NULL CHECK (
    category IN (
      'ACHIEVEMENTS',
      'AXE_FIGHTING',
      'CHARM_POINTS',
      'CLUB_FIGHTING',
      'DISTANCE_FIGHTING',
      'EXPERIENCE',
      'FISHING',
      'FIST_FIGHTING',
      'GOSHNARS_TAINT',
      'LOYALTY_POINTS',
      'MAGIC_LEVEL',
      'SHIELDING',
      'SWORD_FIGHTING',
      'DROME_SCORE',
      'BOSS_POINTS'
    )
  ),
  date DATE NOT NULL,
  value BIGINT,
  rank INTEGER,
  world_id INTEGER NOT NULL REFERENCES worlds (id) ON DELETE CASCADE,
  timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO
  vocations(name,promotion_name)
VALUES
  ('druid', 'elder druid'),
  ('sorcerer', 'master sorcerer'),
  ('knight', 'elite knight'),
  ('paladin', 'royal paladin'),
  ('monk', 'exalted monk');

-- Helpful indexes
CREATE INDEX IF NOT EXISTS idx_scrape_world_time ON scrapes (world_id, scrape_time);

CREATE INDEX IF NOT EXISTS idx_csr_char_cat_date ON character_statrecords (character_id, category, date);

CREATE INDEX IF NOT EXISTS idx_csr_world_cat_date ON character_statrecords (world_id, category, date);