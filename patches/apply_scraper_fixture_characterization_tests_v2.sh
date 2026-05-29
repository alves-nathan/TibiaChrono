#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="scraper-fixture-characterization-tests-v2"
BACKUP_DIR="patches/.backups/${PATCH_NAME}-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

required_files=(
  "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java"
  "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java"
)

mkdir -p "$BACKUP_DIR"
for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "ERROR: required file not found: $file" >&2
    exit 1
  fi
  mkdir -p "$BACKUP_DIR/$(dirname "$file")"
  cp "$file" "$BACKUP_DIR/$file"
done

python3 - <<'PY'
from pathlib import Path
import re

root = Path('.')

highscore = root / 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java'
text = highscore.read_text()
if 'parseHighscoresHtml(String html, String sourceUrl)' not in text:
    old = '''            Document doc = Jsoup.parse(response.body(), url);
            List<HighscoreRow> out = new ArrayList<>();
            Elements rows = doc.select("table.TableContent tr");
            for (Element tr : rows) {
                Elements tds = tr.select("td");
                if (tds.size() < 3) {
                    continue;
                }

                int rank = parseIntSafe(tds.get(0).text());
                String name = tds.get(1).text().trim();
                long value = parseLongSafe(tds.get(tds.size() - 1).text());

                if (rank > 0 && !name.isBlank()) {
                    out.add(new HighscoreRow(rank, name, value));
                }
            }
            return out;
'''
    new = '''            return parseHighscoresHtml(response.body(), url);
'''
    if old not in text:
        raise SystemExit('Could not find inline highscore parsing block in JsoupHighscoreAdapter.java')
    text = text.replace(old, new)
    seam = '''
    List<HighscoreRow> parseHighscoresHtml(String html, String sourceUrl) {
        Document doc = Jsoup.parse(html, sourceUrl);
        List<HighscoreRow> out = new ArrayList<>();
        Elements rows = doc.select("table.TableContent tr");
        for (Element tr : rows) {
            Elements tds = tr.select("td");
            if (tds.size() < 3) {
                continue;
            }

            int rank = parseIntSafe(tds.get(0).text());
            String name = tds.get(1).text().trim();
            long value = parseLongSafe(tds.get(tds.size() - 1).text());

            if (rank > 0 && !name.isBlank()) {
                out.add(new HighscoreRow(rank, name, value));
            }
        }
        return out;
    }

'''
    marker = '    private int mapCategory(StatCategory c) {'
    if marker not in text:
        raise SystemExit('Could not find mapCategory marker in JsoupHighscoreAdapter.java')
    text = text.replace(marker, seam + marker)
    highscore.write_text(text)

worlds = root / 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java'
text = worlds.read_text()
if 'parseWorldsOverviewHtml(String html, String sourceUrl)' not in text:
    old = '''            List<WorldSummary> worlds = new ArrayList<>();
            Elements rows = doc.select("div.TableContentContainer table.TableContent tr");

            for (Element tr : rows) {
                Elements tds = tr.select("> td");
                if (tds.size() < 4) {
                    continue;
                }

                String name = tds.get(0).text().trim();
                if (name.isBlank() || name.equalsIgnoreCase("World")) {
                    continue;
                }

                int online = parseIntSafe(tds.get(1).text());
                String location = tds.get(2).text().trim();
                String pvp = tds.get(3).text().trim();
                String additionalInfo = tds.stream()
                        .skip(4)
                        .map(this::cellTextIncludingImageLabels)
                        .collect(Collectors.joining(" "))
                        .trim();

                String transferType = extractTransferType(additionalInfo).orElse("Regular");
                String gameWorldType = extractGameWorldType(additionalInfo).orElse(null);

                worlds.add(new WorldSummary(name, pvp, location, online, transferType, gameWorldType));
            }

            return worlds;
'''
    new = '''            return parseWorldsOverviewHtml(doc.html(), WORLDS_URL);
'''
    if old not in text:
        raise SystemExit('Could not find inline worlds overview parsing block in JsoupScrapeAdapter.java')
    text = text.replace(old, new)
    seam = '''
    List<WorldSummary> parseWorldsOverviewHtml(String html, String sourceUrl) {
        Document doc = Jsoup.parse(html, sourceUrl);
        List<WorldSummary> worlds = new ArrayList<>();
        Elements rows = doc.select("div.TableContentContainer table.TableContent tr");

        for (Element tr : rows) {
            Elements tds = tr.select("> td");
            if (tds.size() < 4) {
                continue;
            }

            String name = tds.get(0).text().trim();
            if (name.isBlank() || name.equalsIgnoreCase("World")) {
                continue;
            }

            int online = parseIntSafe(tds.get(1).text());
            String location = tds.get(2).text().trim();
            String pvp = tds.get(3).text().trim();
            String additionalInfo = tds.stream()
                    .skip(4)
                    .map(this::cellTextIncludingImageLabels)
                    .collect(Collectors.joining(" "))
                    .trim();

            String transferType = extractTransferType(additionalInfo).orElse("Regular");
            String gameWorldType = extractGameWorldType(additionalInfo).orElse(null);

            worlds.add(new WorldSummary(name, pvp, location, online, transferType, gameWorldType));
        }

        return worlds;
    }

'''
    marker = '    @Override\n    public WorldOnline fetchWorldPage'
    if marker not in text:
        raise SystemExit('Could not find fetchWorldPage marker in JsoupScrapeAdapter.java')
    text = text.replace(marker, seam + marker)
    worlds.write_text(text)

fixtures = root / 'src/test/resources/fixtures/tibia'
fixtures.mkdir(parents=True, exist_ok=True)
(fixtures / 'highscores-experience.html').write_text('''<!doctype html>
<html>
  <body>
    <table class="TableContent">
      <tr class="LabelH"><td>Rank</td><td>Name</td><td>Vocation</td><td>World</td><td>Level</td><td>Points</td></tr>
      <tr><td>1</td><td><a href="?name=Bubble">Bubble</a></td><td>Elder Druid</td><td>Antica</td><td>999</td><td>12,345,678,901</td></tr>
      <tr><td>2.</td><td><a href="?name=Lord%20Paulistinha">Lord Paulistinha</a></td><td>Elite Knight</td><td>Antica</td><td>998</td><td>11 111 111 111</td></tr>
      <tr><td>not a rank</td><td>Ignored Header</td><td>0</td></tr>
      <tr><td>3</td><td>Traded Char (traded)</td><td>Royal Paladin</td><td>Antica</td><td>997</td><td>10.000.000.000</td></tr>
    </table>
  </body>
</html>
''')

(fixtures / 'worlds-overview.html').write_text('''<!doctype html>
<html>
  <body>
    <div class="TableContentContainer">
      <table class="TableContent">
        <tr class="LabelH"><td>World</td><td>Players Online</td><td>Location</td><td> PvP Type </td><td>Additional Information</td></tr>
        <tr>
          <td>Antica</td><td>123</td><td>Europe</td><td>Optional PvP</td>
          <td><img title="blocked transfer" alt="blocked transfer"></td>
          <td><img title="premium game world" alt="premium game world"></td>
        </tr>
        <tr>
          <td>Belobra</td><td>45 players</td><td>South America</td><td>Open PvP</td>
          <td>regular transfer</td>
        </tr>
      </table>
    </div>
  </body>
</html>
''')

base_test = root / 'src/test/java/com/nathan/tibiastats/infrastructure/adapter/scraper'
base_test.mkdir(parents=True, exist_ok=True)
(base_test / 'JsoupHighscoreAdapterCharacterizationTest.java').write_text('''package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.HighscorePort.HighscoreRow;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupHighscoreAdapterCharacterizationTest {

    @Test
    void parsesHighscoreRowsFromFixtureWithoutNetworkAccess() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/fixtures/tibia/highscores-experience.html"));

        List<HighscoreRow> rows = new JsoupHighscoreAdapter()
                .parseHighscoresHtml(html, "https://www.tibia.com/community/?subtopic=highscores");

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).isEqualTo(new HighscoreRow(1, "Bubble", 12_345_678_901L));
        assertThat(rows.get(1)).isEqualTo(new HighscoreRow(2, "Lord Paulistinha", 11_111_111_111L));
        assertThat(rows.get(2)).isEqualTo(new HighscoreRow(3, "Traded Char (traded)", 10_000_000_000L));
    }
}
''')

(base_test / 'JsoupWorldOverviewAdapterCharacterizationTest.java').write_text('''package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.ScrapePort.WorldSummary;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupWorldOverviewAdapterCharacterizationTest {

    @Test
    void parsesWorldOverviewRowsAndImageLabelsFromFixtureWithoutNetworkAccess() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/fixtures/tibia/worlds-overview.html"));

        List<WorldSummary> worlds = new JsoupScrapeAdapter()
                .parseWorldsOverviewHtml(html, "https://www.tibia.com/community/?subtopic=worlds");

        assertThat(worlds).hasSize(2);
        assertThat(worlds.get(0)).isEqualTo(new WorldSummary(
                "Antica",
                "Optional PvP",
                "Europe",
                123,
                "Blocked",
                "Premium"
        ));
        assertThat(worlds.get(1)).isEqualTo(new WorldSummary(
                "Belobra",
                "Open PvP",
                "South America",
                45,
                "regular transfer",
                null
        ));
    }
}
''')
PY

# Java source files should not be executable; normalize permissions to avoid noisy patch warnings.
find src/main/java src/test/java -name '*.java' -type f -exec chmod 0644 {} +

cat <<MSG
Done. Scraper parser seams and fixture characterization tests applied.
Backup directory: $BACKUP_DIR
Next step: make test
MSG
