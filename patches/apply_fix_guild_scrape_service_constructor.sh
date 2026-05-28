#!/usr/bin/env bash
set -euo pipefail

FILE="src/main/java/com/nathan/tibiastats/application/service/GuildScrapeService.java"

if [ ! -f "$FILE" ]; then
  echo "ERROR: $FILE not found. Run this from the project root." >&2
  exit 1
fi

cp "$FILE" "$FILE.bak.$(date +%Y%m%d%H%M%S)"

python3 - <<'PY'
from pathlib import Path
path = Path("src/main/java/com/nathan/tibiastats/application/service/GuildScrapeService.java")
text = path.read_text()

if "org.springframework.beans.factory.annotation.Autowired" not in text:
    text = text.replace(
        "import org.slf4j.LoggerFactory;\n",
        "import org.slf4j.LoggerFactory;\nimport org.springframework.beans.factory.annotation.Autowired;\n",
    )

needle = "    public GuildScrapeService(GuildScrapePort scraper,"
if "@Autowired\n    public GuildScrapeService(GuildScrapePort scraper," not in text:
    text = text.replace(needle, "    @Autowired\n" + needle)

path.write_text(text)
PY

echo "GuildScrapeService constructor fixed with @Autowired."
echo "Run: ./run-tests.sh && then start the app again."
