#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="scraper-fixture-characterization-tests-v4"
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


def insert_before_last_class_brace(text: str, method: str, marker_candidates: list[str], file_name: str) -> str:
    for marker in marker_candidates:
        if marker in text:
            return text.replace(marker, method + marker, 1)

    idx = text.rfind('\n}')
    if idx == -1:
        raise SystemExit(f'Could not find class closing brace in {file_name}')
    return text[:idx] + method + text[idx:]


highscore = root / 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java'
text = highscore.read_text()

if 'parseHighscoresHtml(String html, String sourceUrl)' not in text:
    # Prefer wiring the production path to the seam when the current implementation still has inline parsing.
    inline_pattern = re.compile(
        r'''(?P<indent>\s*)Document\s+doc\s*=\s*Jsoup\.parse\(response\.body\(\),\s*url\);\n'''
        r'''(?P=indent)List<HighscoreRow>\s+out\s*=\s*new\s+ArrayList<>\(\);\n'''
        r'''(?P=indent)Elements\s+rows\s*=\s*doc\.select\("table\.TableContent tr"\);\n'''
        r'''(?P<body>.*?)'''
        r'''(?P=indent)return\s+out;\n''',
        re.DOTALL,
    )

    def replace_highscore_inline(match: re.Match) -> str:
        indent = match.group('indent')
        return f'{indent}return parseHighscoresHtml(response.body(), url);\n'

    text, replacements = inline_pattern.subn(replace_highscore_inline, text, count=1)
    if replacements == 0:
        print('WARN: highscore fetch path was not rewritten; adding parser seam for characterization tests only.')

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
    text = insert_before_last_class_brace(
        text,
        seam,
        ['    private int mapCategory(StatCategory c) {', '    private static int mapCategory(StatCategory c) {'],
        str(highscore),
    )
    highscore.write_text(text)

worlds = root / 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java'
text = worlds.read_text()

if 'parseWorldsOverviewHtml(String html, String sourceUrl)' not in text:
    inline_pattern = re.compile(
        r'''(?P<indent>\s*)List<WorldSummary>\s+worlds\s*=\s*new\s+ArrayList<>\(\);\n'''
        r'''(?P=indent)Elements\s+rows\s*=\s*doc\.select\("div\.TableContentContainer table\.TableContent tr"\);\n'''
        r'''(?P<body>.*?)'''
        r'''(?P=indent)return\s+worlds;\n''',
        re.DOTALL,
    )

    def replace_worlds_inline(match: re.Match) -> str:
        indent = match.group('indent')
        return f'{indent}return parseWorldsOverviewHtml(doc.html(), WORLDS_URL);\n'

    text, replacements = inline_pattern.subn(replace_worlds_inline, text, count=1)
    if replacements == 0:
        print('WARN: worlds overview fetch path was not rewritten; adding parser seam for characterization tests only.')

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
    text = insert_before_last_class_brace(
        text,
        seam,
        ['    @Override\n    public WorldOnline fetchWorldPage', '    public WorldOnline fetchWorldPage'],
        str(worlds),
    )
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

(base_test / 'JsoupScrapeAdapterCharacterizationTest.java').write_text('''package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.ScrapePort.WorldSummary;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupScrapeAdapterCharacterizationTest {

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
