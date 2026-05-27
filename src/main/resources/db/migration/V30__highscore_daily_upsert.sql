-- Highscores are daily snapshots. Keep only one row per character/world/category/date.
-- If older experiments created duplicates, keep the most recently scraped row.

delete from character_statrecords older
using character_statrecords newer
where older.character_id = newer.character_id
  and older.world_id = newer.world_id
  and older.category = newer.category
  and older.date = newer.date
  and older.id < newer.id;

create unique index if not exists ux_character_statrecords_daily_identity
    on character_statrecords(character_id, world_id, category, date);

create index if not exists idx_csr_world_cat_rank_date
    on character_statrecords(world_id, category, date, rank);

create index if not exists idx_csr_scraped_at
    on character_statrecords(scraped_at);
