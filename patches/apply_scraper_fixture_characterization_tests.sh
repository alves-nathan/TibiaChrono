#!/usr/bin/env bash
set -Eeuo pipefail

if [[ ! -f "pom.xml" || ! -d "src/main/java/com/nathan/tibiastats" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

if ! command -v git >/dev/null 2>&1; then
  echo "ERROR: git is required to apply this patch cleanly." >&2
  exit 1
fi

STAMP="$(date +%Y%m%d%H%M%S)"
BACKUP_DIR="patches/.backups/scraper-fixture-characterization-tests-$STAMP"
mkdir -p "$BACKUP_DIR"

for f in 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java' 'src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java'; do
  if [[ -f "$f" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$f")"
    cp "$f" "$BACKUP_DIR/$f"
  fi
done

PATCH_FILE="$(mktemp)"
trap 'rm -f "$PATCH_FILE"' EXIT
cat > "$PATCH_FILE" <<'PATCH_EOF'
diff --git a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java
index c249855..e74618c 100644
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapter.java
@@ -56,23 +56,7 @@ public class JsoupHighscoreAdapter implements HighscorePort {
             }
 
             Document doc = Jsoup.parse(response.body(), url);
-            List<HighscoreRow> out = new ArrayList<>();
-            Elements rows = doc.select("table.TableContent tr");
-            for (Element tr : rows) {
-                Elements tds = tr.select("td");
-                if (tds.size() < 3) {
-                    continue;
-                }
-
-                int rank = parseIntSafe(tds.get(0).text());
-                String name = tds.get(1).text().trim();
-                long value = parseLongSafe(tds.get(tds.size() - 1).text());
-
-                if (rank > 0 && !name.isBlank()) {
-                    out.add(new HighscoreRow(rank, name, value));
-                }
-            }
-            return out;
+            return parseHighscoreDocument(doc);
         } catch (IOException e) {
             throw new RuntimeException("Failed to fetch highscores: world=" + world
                     + ", category=" + category
@@ -87,6 +71,26 @@ public class JsoupHighscoreAdapter implements HighscorePort {
         }
     }
 
+    List<HighscoreRow> parseHighscoreDocument(Document doc) {
+        List<HighscoreRow> out = new ArrayList<>();
+        Elements rows = doc.select("table.TableContent tr");
+        for (Element tr : rows) {
+            Elements tds = tr.select("td");
+            if (tds.size() < 3) {
+                continue;
+            }
+
+            int rank = parseIntSafe(tds.get(0).text());
+            String name = tds.get(1).text().trim();
+            long value = parseLongSafe(tds.get(tds.size() - 1).text());
+
+            if (rank > 0 && !name.isBlank()) {
+                out.add(new HighscoreRow(rank, name, value));
+            }
+        }
+        return out;
+    }
+
     private int mapCategory(StatCategory c) {
         return switch (c) {
             case ACHIEVEMENTS -> 1;
diff --git a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java
index fbdbe2a..5d3171e 100644
--- a/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java
+++ b/src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapter.java
@@ -36,40 +36,43 @@ public class JsoupScrapeAdapter implements ScrapePort {
                     .userAgent(USER_AGENT)
                     .timeout(TIMEOUT_MS)
                     .get();
+            return parseWorldsOverviewDocument(doc);
+        } catch (IOException e) {
+            throw new RuntimeException(e);
+        }
+    }
 
-            List<WorldSummary> worlds = new ArrayList<>();
-            Elements rows = doc.select("div.TableContentContainer table.TableContent tr");
-
-            for (Element tr : rows) {
-                Elements tds = tr.select("> td");
-                if (tds.size() < 4) {
-                    continue;
-                }
+    List<WorldSummary> parseWorldsOverviewDocument(Document doc) {
+        List<WorldSummary> worlds = new ArrayList<>();
+        Elements rows = doc.select("div.TableContentContainer table.TableContent tr");
 
-                String name = tds.get(0).text().trim();
-                if (name.isBlank() || name.equalsIgnoreCase("World")) {
-                    continue;
-                }
+        for (Element tr : rows) {
+            Elements tds = tr.select("> td");
+            if (tds.size() < 4) {
+                continue;
+            }
 
-                int online = parseIntSafe(tds.get(1).text());
-                String location = tds.get(2).text().trim();
-                String pvp = tds.get(3).text().trim();
-                String additionalInfo = tds.stream()
-                        .skip(4)
-                        .map(this::cellTextIncludingImageLabels)
-                        .collect(Collectors.joining(" "))
-                        .trim();
+            String name = tds.get(0).text().trim();
+            if (name.isBlank() || name.equalsIgnoreCase("World")) {
+                continue;
+            }
 
-                String transferType = extractTransferType(additionalInfo).orElse("Regular");
-                String gameWorldType = extractGameWorldType(additionalInfo).orElse(null);
+            int online = parseIntSafe(tds.get(1).text());
+            String location = tds.get(2).text().trim();
+            String pvp = tds.get(3).text().trim();
+            String additionalInfo = tds.stream()
+                    .skip(4)
+                    .map(this::cellTextIncludingImageLabels)
+                    .collect(Collectors.joining(" "))
+                    .trim();
 
-                worlds.add(new WorldSummary(name, pvp, location, online, transferType, gameWorldType));
-            }
+            String transferType = extractTransferType(additionalInfo).orElse("Regular");
+            String gameWorldType = extractGameWorldType(additionalInfo).orElse(null);
 
-            return worlds;
-        } catch (IOException e) {
-            throw new RuntimeException(e);
+            worlds.add(new WorldSummary(name, pvp, location, online, transferType, gameWorldType));
         }
+
+        return worlds;
     }
 
     @Override
@@ -80,84 +83,87 @@ public class JsoupScrapeAdapter implements ScrapePort {
                     .userAgent(USER_AGENT)
                     .timeout(TIMEOUT_MS)
                     .get();
+            return parseWorldPageDocument(doc, worldName, world);
+        } catch (IOException e) {
+            throw new RuntimeException(e);
+        }
+    }
 
-            int online = 0;
-            List<OnlineCharacterSnapshot> players = new ArrayList<>();
-
-            Elements rowsT1 = doc.select("table.Table1 div.InnerTableContainer tbody tr");
-            for (Element tr : rowsT1) {
-                String rowText = tr.text();
-                String value = lastCellText(tr);
-
-                if (rowText.contains("Players Online:")) {
-                    online = parseIntSafe(value);
-                    continue;
-                }
+    WorldOnline parseWorldPageDocument(Document doc, String worldName, World world) {
+        int online = 0;
+        List<OnlineCharacterSnapshot> players = new ArrayList<>();
 
-                if (rowText.contains("Creation Date:") && world.getCreationDate() == null) {
-                    DateTimeFormatter formatter = new DateTimeFormatterBuilder()
-                            .appendPattern("MMMM uuuu")
-                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
-                            .toFormatter(Locale.ENGLISH);
-                    world.setCreationDate(LocalDate.parse(value, formatter));
-                    continue;
-                }
+        Elements rowsT1 = doc.select("table.Table1 div.InnerTableContainer tbody tr");
+        for (Element tr : rowsT1) {
+            String rowText = tr.text();
+            String value = lastCellText(tr);
 
-                if (rowText.contains("Online Record:") && world.getOnlineRecord() == null) {
-                    world.setOnlineRecord(value);
-                    continue;
-                }
+            if (rowText.contains("Players Online:")) {
+                online = parseIntSafe(value);
+                continue;
+            }
 
-                if (rowText.contains("PvP Type:") && world.getPvpType() == null) {
-                    world.setPvpType(value);
-                    continue;
-                }
+            if (rowText.contains("Creation Date:") && world.getCreationDate() == null) {
+                DateTimeFormatter formatter = new DateTimeFormatterBuilder()
+                        .appendPattern("MMMM uuuu")
+                        .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
+                        .toFormatter(Locale.ENGLISH);
+                world.setCreationDate(LocalDate.parse(value, formatter));
+                continue;
+            }
 
-                if (rowText.contains("Transfer Type:")) {
-                    world.setTransferType(value);
-                    continue;
-                }
+            if (rowText.contains("Online Record:") && world.getOnlineRecord() == null) {
+                world.setOnlineRecord(value);
+                continue;
+            }
 
-                if (rowText.contains("Game World Type:")) {
-                    world.setGameWorldType(value);
-                }
+            if (rowText.contains("PvP Type:") && world.getPvpType() == null) {
+                world.setPvpType(value);
+                continue;
             }
 
-            Elements rowsT2 = doc.select("table.Table2 div.InnerTableContainer tr");
-            for (Element tr : rowsT2) {
-                String rowText = tr.text();
-                if (rowText.contains("Name [sort] Level [sort] Vocation [sort]")) {
-                    continue;
-                }
+            if (rowText.contains("Transfer Type:")) {
+                world.setTransferType(value);
+                continue;
+            }
 
-                Elements cols = tr.select("> td");
-                if (cols.isEmpty()) {
-                    continue;
-                }
+            if (rowText.contains("Game World Type:")) {
+                world.setGameWorldType(value);
+            }
+        }
 
-                String name = tr.select("a[href*=?name=]").text().trim();
-                if (name.isBlank()) {
-                    continue;
-                }
+        Elements rowsT2 = doc.select("table.Table2 div.InnerTableContainer tr");
+        for (Element tr : rowsT2) {
+            String rowText = tr.text();
+            if (rowText.contains("Name [sort] Level [sort] Vocation [sort]")) {
+                continue;
+            }
 
-                Integer level = cols.size() > 1 ? parseIntegerOrNull(cols.get(1).text()) : null;
-                String vocation = cols.size() > 2 ? blankToNull(cols.get(2).text()) : null;
+            Elements cols = tr.select("> td");
+            if (cols.isEmpty()) {
+                continue;
+            }
 
-                players.add(new OnlineCharacterSnapshot(name, level, vocation));
+            String name = tr.select("a[href*=?name=]").text().trim();
+            if (name.isBlank()) {
+                continue;
             }
 
-            return new WorldOnline(
-                    worldName,
-                    online,
-                    players,
-                    world.getOnlineRecord(),
-                    world.getCreationDate(),
-                    world.getTransferType(),
-                    world.getGameWorldType()
-            );
-        } catch (IOException e) {
-            throw new RuntimeException(e);
+            Integer level = cols.size() > 1 ? parseIntegerOrNull(cols.get(1).text()) : null;
+            String vocation = cols.size() > 2 ? blankToNull(cols.get(2).text()) : null;
+
+            players.add(new OnlineCharacterSnapshot(name, level, vocation));
         }
+
+        return new WorldOnline(
+                worldName,
+                online,
+                players,
+                world.getOnlineRecord(),
+                world.getCreationDate(),
+                world.getTransferType(),
+                world.getGameWorldType()
+        );
     }
 
     @Override
diff --git a/src/test/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapterCharacterizationTest.java b/src/test/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapterCharacterizationTest.java
new file mode 100644
index 0000000..cb2d4c2
--- /dev/null
+++ b/src/test/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupHighscoreAdapterCharacterizationTest.java
@@ -0,0 +1,39 @@
+package com.nathan.tibiastats.infrastructure.adapter.scraper;
+
+import com.nathan.tibiastats.domain.port.HighscorePort;
+import org.jsoup.Jsoup;
+import org.jsoup.nodes.Document;
+import org.junit.jupiter.api.Test;
+
+import java.nio.charset.StandardCharsets;
+import java.util.List;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+class JsoupHighscoreAdapterCharacterizationTest {
+
+    @Test
+    void parsesHighscoreRowsFromFixture() throws Exception {
+        Document doc = Jsoup.parse(
+                fixture("/fixtures/tibia/highscores-experience-antica.html"),
+                "https://www.tibia.com/community/?subtopic=highscores"
+        );
+
+        List<HighscorePort.HighscoreRow> rows = new JsoupHighscoreAdapter().parseHighscoreDocument(doc);
+
+        assertThat(rows).hasSize(2);
+        assertThat(rows.get(0).rank()).isEqualTo(1);
+        assertThat(rows.get(0).name()).isEqualTo("Eternal Oblivion");
+        assertThat(rows.get(0).value()).isEqualTo(1_234_567_890L);
+        assertThat(rows.get(1).rank()).isEqualTo(2);
+        assertThat(rows.get(1).name()).isEqualTo("Bubble");
+        assertThat(rows.get(1).value()).isEqualTo(987_654_321L);
+    }
+
+    private String fixture(String path) throws Exception {
+        try (var in = getClass().getResourceAsStream(path)) {
+            assertThat(in).as("fixture %s", path).isNotNull();
+            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
+        }
+    }
+}
diff --git a/src/test/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapterCharacterizationTest.java b/src/test/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapterCharacterizationTest.java
new file mode 100644
index 0000000..18ac7c1
--- /dev/null
+++ b/src/test/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupScrapeAdapterCharacterizationTest.java
@@ -0,0 +1,68 @@
+package com.nathan.tibiastats.infrastructure.adapter.scraper;
+
+import com.nathan.tibiastats.domain.model.World;
+import com.nathan.tibiastats.domain.port.ScrapePort;
+import org.jsoup.Jsoup;
+import org.jsoup.nodes.Document;
+import org.junit.jupiter.api.Test;
+
+import java.nio.charset.StandardCharsets;
+import java.time.LocalDate;
+import java.util.List;
+
+import static org.assertj.core.api.Assertions.assertThat;
+
+class JsoupScrapeAdapterCharacterizationTest {
+
+    private final JsoupScrapeAdapter adapter = new JsoupScrapeAdapter();
+
+    @Test
+    void parsesWorldOverviewRowsFromFixture() throws Exception {
+        Document doc = Jsoup.parse(fixture("/fixtures/tibia/worlds-overview.html"));
+
+        List<ScrapePort.WorldSummary> worlds = adapter.parseWorldsOverviewDocument(doc);
+
+        assertThat(worlds).hasSize(2);
+        assertThat(worlds.get(0)).isEqualTo(new ScrapePort.WorldSummary(
+                "Antica",
+                "Open PvP",
+                "Europe",
+                321,
+                "Regular",
+                "Premium"
+        ));
+        assertThat(worlds.get(1)).isEqualTo(new ScrapePort.WorldSummary(
+                "Bombra",
+                "Optional PvP",
+                "South America",
+                42,
+                "Blocked",
+                null
+        ));
+    }
+
+    @Test
+    void parsesWorldDetailAndOnlineCharactersFromFixture() throws Exception {
+        Document doc = Jsoup.parse(fixture("/fixtures/tibia/world-antica.html"));
+        World world = new World("Antica", "Open PvP", "Europe");
+
+        ScrapePort.WorldOnline online = adapter.parseWorldPageDocument(doc, "Antica", world);
+
+        assertThat(online.playersOnline()).isEqualTo(2);
+        assertThat(online.onlineRecord()).isEqualTo("1050 players (on Jan 01 2020)");
+        assertThat(online.creationDate()).isEqualTo(LocalDate.of(2004, 1, 1));
+        assertThat(online.transferType()).isEqualTo("Regular");
+        assertThat(online.gameWorldType()).isEqualTo("Premium");
+        assertThat(online.players()).containsExactly(
+                new ScrapePort.OnlineCharacterSnapshot("Knight One", 400, "Elite Knight"),
+                new ScrapePort.OnlineCharacterSnapshot("Druid Two", 350, "Elder Druid")
+        );
+    }
+
+    private String fixture(String path) throws Exception {
+        try (var in = getClass().getResourceAsStream(path)) {
+            assertThat(in).as("fixture %s", path).isNotNull();
+            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
+        }
+    }
+}
diff --git a/src/test/resources/fixtures/tibia/highscores-experience-antica.html b/src/test/resources/fixtures/tibia/highscores-experience-antica.html
new file mode 100644
index 0000000..7ac4548
--- /dev/null
+++ b/src/test/resources/fixtures/tibia/highscores-experience-antica.html
@@ -0,0 +1,13 @@
+<!doctype html>
+<html>
+<body>
+<div class="TableContentContainer">
+<table class="TableContent">
+<tr><td>Rank</td><td>Name</td><td>World</td><td>Level</td><td>XP</td></tr>
+<tr><td>1.</td><td><a href="?subtopic=characters&name=Eternal%20Oblivion">Eternal Oblivion</a></td><td>Antica</td><td>999</td><td>1,234,567,890</td></tr>
+<tr><td>2.</td><td><a href="?subtopic=characters&name=Bubble">Bubble</a></td><td>Antica</td><td>888</td><td>987,654,321</td></tr>
+<tr><td></td><td></td><td></td></tr>
+</table>
+</div>
+</body>
+</html>
diff --git a/src/test/resources/fixtures/tibia/world-antica.html b/src/test/resources/fixtures/tibia/world-antica.html
new file mode 100644
index 0000000..58033eb
--- /dev/null
+++ b/src/test/resources/fixtures/tibia/world-antica.html
@@ -0,0 +1,22 @@
+<!doctype html>
+<html>
+<body>
+<table class="Table1">
+  <tr><td><div class="InnerTableContainer"><table><tbody>
+    <tr><td>Players Online:</td><td>2</td></tr>
+    <tr><td>Creation Date:</td><td>January 2004</td></tr>
+    <tr><td>Online Record:</td><td>1050 players (on Jan 01 2020)</td></tr>
+    <tr><td>PvP Type:</td><td>Open PvP</td></tr>
+    <tr><td>Transfer Type:</td><td>Regular</td></tr>
+    <tr><td>Game World Type:</td><td>Premium</td></tr>
+  </tbody></table></div></td></tr>
+</table>
+<table class="Table2">
+  <tr><td><div class="InnerTableContainer"><table>
+    <tr><td>Name [sort]</td><td>Level [sort]</td><td>Vocation [sort]</td></tr>
+    <tr><td><a href="?subtopic=characters&name=Knight%20One">Knight One</a></td><td>400</td><td>Elite Knight</td></tr>
+    <tr><td><a href="?subtopic=characters&name=Druid%20Two">Druid Two</a></td><td>350</td><td>Elder Druid</td></tr>
+  </table></div></td></tr>
+</table>
+</body>
+</html>
diff --git a/src/test/resources/fixtures/tibia/worlds-overview.html b/src/test/resources/fixtures/tibia/worlds-overview.html
new file mode 100644
index 0000000..7a05ed9
--- /dev/null
+++ b/src/test/resources/fixtures/tibia/worlds-overview.html
@@ -0,0 +1,12 @@
+<!doctype html>
+<html>
+<body>
+<div class="TableContentContainer">
+<table class="TableContent">
+<tr><td>World</td><td>Players Online</td><td>Location</td><td>PvP Type</td><td>Additional Information</td></tr>
+<tr><td>Antica</td><td>321</td><td>Europe</td><td>Open PvP</td><td><img title="Premium" alt="Premium" /> Premium game world</td></tr>
+<tr><td>Bombra</td><td>42</td><td>South America</td><td>Optional PvP</td><td><img title="Blocked" alt="Blocked" /> Blocked world transfer</td></tr>
+</table>
+</div>
+</body>
+</html>

PATCH_EOF

if git apply --check "$PATCH_FILE"; then
  git apply --whitespace=nowarn "$PATCH_FILE"
else
  echo "ERROR: patch does not apply cleanly. Backup was created at $BACKUP_DIR" >&2
  echo "Tip: verify that previous architecture patches were applied in order." >&2
  exit 1
fi

echo "Done. Scraper parser seams and fixture characterization tests applied."
echo "Backup directory: $BACKUP_DIR"
echo "Next step: make test"
