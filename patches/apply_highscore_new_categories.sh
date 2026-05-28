#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

required=(
  "src/main/java/com/nathan/tibiastats/domain/model/StatCategory.java"
  "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java"
  "src/main/resources/graphql/schema.graphqls"
  "src/main/resources/application-dev.yml"
  "src/main/resources/application.yml"
  "docker-compose.dev.yml"
)

for file in "${required[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing required file: $file" >&2
    exit 1
  fi
done

BACKUP_DIR=".tibiachrono-highscore-new-categories-backup-$(date +%Y%m%d%H%M%S)"
mkdir -p "$BACKUP_DIR"
for file in "${required[@]}"; do
  mkdir -p "$BACKUP_DIR/$(dirname "$file")"
  cp "$file" "$BACKUP_DIR/$file"
done

cat > src/main/java/com/nathan/tibiastats/domain/model/StatCategory.java <<'JAVA'
package com.nathan.tibiastats.domain.model;

public enum StatCategory {
    ACHIEVEMENTS,
    AXE_FIGHTING,
    BOSS_POINTS,
    BOUNTY_POINTS_EARNED,
    CHARM_POINTS,
    CLUB_FIGHTING,
    DISTANCE_FIGHTING,
    DROME_SCORE,
    EXPERIENCE,
    FISHING,
    FIST_FIGHTING,
    GOSHNARS_TAINT,
    LOYALTY_POINTS,
    MAGIC_LEVEL,
    SHIELDING,
    SWORD_FIGHTING,
    WEEKLY_TASKS_COMPLETED
}
JAVA

python3 - <<'PY'
from pathlib import Path
import re

categories_csv = "ACHIEVEMENTS,AXE_FIGHTING,BOSS_POINTS,BOUNTY_POINTS_EARNED,CHARM_POINTS,CLUB_FIGHTING,DISTANCE_FIGHTING,DROME_SCORE,EXPERIENCE,FISHING,FIST_FIGHTING,GOSHNARS_TAINT,LOYALTY_POINTS,MAGIC_LEVEL,SHIELDING,SWORD_FIGHTING,WEEKLY_TASKS_COMPLETED"

# 1) Update highscore category-id mapping.
p = Path("src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java")
s = p.read_text()
new_switch = '''    private int mapCategory(StatCategory c) {
        return switch (c) {
            case ACHIEVEMENTS -> 1;
            case AXE_FIGHTING -> 2;
            case CHARM_POINTS -> 3;
            case CLUB_FIGHTING -> 4;
            case DISTANCE_FIGHTING -> 5;
            case EXPERIENCE -> 6;
            case FISHING -> 7;
            case FIST_FIGHTING -> 8;
            case GOSHNARS_TAINT -> 9;
            case LOYALTY_POINTS -> 10;
            case MAGIC_LEVEL -> 11;
            case SHIELDING -> 12;
            case SWORD_FIGHTING -> 13;
            case DROME_SCORE -> 14;
            case BOSS_POINTS -> 15;
            case BOUNTY_POINTS_EARNED -> 16;
            case WEEKLY_TASKS_COMPLETED -> 17;
        };
    }'''
s2 = re.sub(r"    private int mapCategory\(StatCategory c\) \{\n        return switch \(c\) \{.*?\n        \};\n    \}", new_switch, s, flags=re.S)
if s2 == s:
    raise SystemExit("Could not replace mapCategory(...) in JsoupHighscoreAdapter.java")
p.write_text(s2)

# 2) Update GraphQL enum.
p = Path("src/main/resources/graphql/schema.graphqls")
s = p.read_text()
new_enum = '''enum StatCategory {
    ACHIEVEMENTS
    AXE_FIGHTING
    BOSS_POINTS
    BOUNTY_POINTS_EARNED
    CHARM_POINTS
    CLUB_FIGHTING
    DISTANCE_FIGHTING
    DROME_SCORE
    EXPERIENCE
    FISHING
    FIST_FIGHTING
    GOSHNARS_TAINT
    LOYALTY_POINTS
    MAGIC_LEVEL
    SHIELDING
    SWORD_FIGHTING
    WEEKLY_TASKS_COMPLETED
}'''
s2 = re.sub(r"enum StatCategory \{.*?\n\}", new_enum, s, count=1, flags=re.S)
if s2 == s:
    raise SystemExit("Could not replace StatCategory enum in schema.graphqls")
p.write_text(s2)

# 3) Update application-dev.yml and application.yml highscore blocks using Spring property names, not env-var names.
def replace_highscores_block(path: str, cron: str, comment: str = ""):
    p = Path(path)
    s = p.read_text()
    replacement = f'''    highscores:
      enabled: true
      {comment}cron: "{cron}"
      categories: "{categories_csv}"
      vocations: "0,1,2,3,4,5,6"
      max-pages: 100
      page-delay-ms: 1000
      world-limit: 0
'''
    # Replace the highscores block inside tibiastats.scrape until the next sibling under tibiastats or jwt.
    pattern = r"    highscores:\n(?:      .*\n)+?(?=  jwt:|    [a-zA-Z0-9_-]+:|\Z)"
    s2, n = re.subn(pattern, replacement, s, count=1)
    if n == 0:
        raise SystemExit(f"Could not replace highscores block in {path}")
    p.write_text(s2)

replace_highscores_block(
    "src/main/resources/application-dev.yml",
    "0 */5 * * * *",
    '# For dev/testing use: "0 */5 * * * *". For daily run use: "0 0 7 * * *".\n      '
)
replace_highscores_block("src/main/resources/application.yml", "0 0 7 * * *", "")

# 4) Remove scraper-specific environment overrides from docker-compose.dev.yml so application-dev.yml is authoritative.
p = Path("docker-compose.dev.yml")
s = p.read_text().splitlines(True)
remove_keys = {
    "TIBIASTATS_SCRAPE_WORLDS_RATE_MS",
    "SPRING_TASK_SCHEDULING_POOL_SIZE",
    "TIBIASTATS_SCRAPE_CHARACTER_DETAILS_ENABLED",
    "TIBIASTATS_SCRAPE_CHARACTER_DETAILS_RATE_MS",
    "TIBIASTATS_SCRAPE_CHARACTER_DETAILS_INITIAL_DELAY_MS",
    "TIBIASTATS_SCRAPE_CHARACTER_DETAILS_BATCH_SIZE",
    "TIBIASTATS_SCRAPE_HIGHSCORES_CRON",
    "TIBIASTATS_SCRAPE_HIGHSCORES_ENABLED",
    "TIBIASTATS_SCRAPE_HIGHSCORES_CATEGORIES",
    "TIBIASTATS_SCRAPE_HIGHSCORES_VOCATIONS",
    "TIBIASTATS_SCRAPE_HIGHSCORES_MAX_PAGES",
    "TIBIASTATS_SCRAPE_HIGHSCORES_PAGE_DELAY_MS",
    "TIBIASTATS_SCRAPE_HIGHSCORES_WORLD_LIMIT",
}
out = []
for line in s:
    stripped = line.strip()
    if any(stripped.startswith(k + ":") for k in remove_keys):
        continue
    out.append(line)
p.write_text("".join(out))
PY

mkdir -p src/main/resources/db/migration
cat > src/main/resources/db/migration/V32__highscore_new_categories.sql <<'SQL'
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
SQL

echo "Highscore categories updated. Backup created at: $BACKUP_DIR"
echo "New categories added: BOUNTY_POINTS_EARNED, WEEKLY_TASKS_COMPLETED"
echo "Highscore scraper config now lives in application-dev.yml/application.yml, not docker-compose.dev.yml."
