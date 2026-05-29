#!/usr/bin/env bash
set -Eeuo pipefail

PATCH_NAME="character-guild-scraper-fixture-characterization-tests"
BACKUP_DIR="patches/.backups/${PATCH_NAME}-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
backup_if_exists() {
  local file="$1"
  if [[ -f "$file" ]]; then
    mkdir -p "$BACKUP_DIR/$(dirname "$file")"
    cp "$file" "$BACKUP_DIR/$file"
  fi
}

for file in \
  "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupCharacterAdapter.java" \
  "src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java"; do
  backup_if_exists "$file"
done

python3 - <<'PY'
from pathlib import Path
import re

fixtures = Path('src/test/resources/fixtures/tibia')
fixtures.mkdir(parents=True, exist_ok=True)
tests = Path('src/test/java/com/nathan/tibiastats/infrastructure/adapter/scraper')
tests.mkdir(parents=True, exist_ok=True)

character = Path('src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupCharacterAdapter.java')
text = character.read_text()
if 'parseCharacterDetailsHtml(String html, String characterName)' not in text:
    fetch_start = text.index('    @Override\n    public Optional<CharacterDetails> fetchCharacterDetails(String characterName)')
    body_start = text.index('            if (isCharacterNotFound(doc)) {', fetch_start)
    catch_start = text.index('        } catch (HttpStatusException e) {', body_start)
    body = text[body_start:catch_start]
    deindented = re.sub(r'(?m)^            ', '        ', body).rstrip() + '\n'
    text = text[:body_start] + '            return parseCharacterDetailsDocument(doc, characterName);\n' + text[catch_start:]
    insert_marker = '    private Map<String, String> collectCharacterFields(Document doc) {'
    insert_at = text.index(insert_marker)
    methods = f'''
    Optional<CharacterDetails> parseCharacterDetailsHtml(String html, String characterName) {{
        Document doc = Jsoup.parse(html == null ? "" : html);
        return parseCharacterDetailsDocument(doc, characterName);
    }}

    Optional<CharacterDetails> parseCharacterDetailsDocument(Document doc, String characterName) {{
{deindented}    }}

'''
    text = text[:insert_at] + methods + text[insert_at:]
    character.write_text(text)

guild = Path('src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java')
text = guild.read_text()
if 'parseGuildDetailHtml(String html, String guildName)' not in text:
    fetch_start = text.index('    @Override\n    public GuildDetail fetchGuildDetail(String guildName)')
    body_start = text.index('            String pageText = doc.text();', fetch_start)
    catch_start = text.index('        } catch (IOException e) {', body_start)
    body = text[body_start:catch_start]
    deindented = re.sub(r'(?m)^            ', '        ', body).rstrip() + '\n'
    text = text[:body_start] + '            return parseGuildDetailDocument(doc, guildName);\n' + text[catch_start:]
    insert_marker = '    private List<Member> parseMembers(Document doc) {'
    insert_at = text.index(insert_marker)
    methods = f'''
    GuildDetail parseGuildDetailHtml(String html, String guildName) {{
        Document doc = Jsoup.parse(html == null ? "" : html, BASE_URL);
        return parseGuildDetailDocument(doc, guildName);
    }}

    GuildDetail parseGuildDetailDocument(Document doc, String guildName) {{
{deindented}    }}

'''
    text = text[:insert_at] + methods + text[insert_at:]
    guild.write_text(text)

(fixtures / 'character-detail.html').write_text('''<!doctype html>
<html>
  <body>
    <table class="TableContent">
      <tr><td class="LabelV">Name:</td><td>Sample Char</td></tr>
      <tr><td class="LabelV">Former Names:</td><td>Old Sample, Older Sample</td></tr>
      <tr><td class="LabelV">Sex:</td><td>male</td></tr>
      <tr><td class="LabelV">Vocation:</td><td>Elite Knight</td></tr>
      <tr><td class="LabelV">Level:</td><td>123</td></tr>
      <tr><td class="LabelV">Achievement Points:</td><td>456</td></tr>
      <tr><td class="LabelV">Residence:</td><td>Thais</td></tr>
      <tr><td class="LabelV">Last Login:</td><td>May 27 2026, 21:15:33 CEST</td></tr>
      <tr><td class="LabelV">Account Status:</td><td>Premium Account</td></tr>
      <tr><td class="LabelV">Created:</td><td>May 1 2020, 10:00:00 CET</td></tr>
      <tr><td class="LabelV">World:</td><td>Antica</td></tr>
    </table>
  </body>
</html>
''')

(fixtures / 'guild-detail.html').write_text('''<!doctype html>
<html>
  <body>
    <div class="BoxContent">
      Guild Information Raw Raw World: Antica Guild Description: Neutral guild Homepage: https://example.test Founded: May 1 2024 Members: 2 members 1 online
    </div>
    <table class="TableContent">
      <tr><td>World:</td><td>Antica</td></tr>
      <tr><td>Guild Description:</td><td>Neutral guild</td></tr>
      <tr><td>Homepage:</td><td>https://example.test</td></tr>
      <tr><td>Founded:</td><td>May 1 2024</td></tr>
    </table>
    <table class="TableContent">
      <tr><td>Name and title</td><td>Vocation</td><td>Level</td><td>Joining Date</td><td>Status</td></tr>
      <tr><td colspan="5">Leaders</td></tr>
      <tr>
        <td><a href="?subtopic=characters&amp;name=Guild%20Leader">Guild Leader</a> The Boss</td>
        <td>Elite Knight</td><td>500</td><td>May 1 2026</td><td>online</td>
      </tr>
      <tr><td colspan="5">Members</td></tr>
      <tr>
        <td><a href="?subtopic=characters&amp;name=Guild%20Member">Guild Member</a></td>
        <td>Elder Druid</td><td>250</td><td>May 2 2026</td><td>offline</td>
      </tr>
    </table>
    <table class="TableContent">
      <tr><td>Invited Characters</td></tr>
      <tr><td><a href="?subtopic=characters&amp;name=Invited%20Char">Invited Char</a></td><td>May 3 2026</td></tr>
    </table>
  </body>
</html>
''')

(tests / 'JsoupCharacterAdapterCharacterizationTest.java').write_text('''package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.CharacterEntity;
import com.nathan.tibiastats.domain.port.CharacterDetailPort.CharacterDetails;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupCharacterAdapterCharacterizationTest {

    @Test
    void parsesCharacterProfileFieldsFromFixtureWithoutNetworkAccess() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/fixtures/tibia/character-detail.html"));

        Optional<CharacterDetails> result = new JsoupCharacterAdapter()
                .parseCharacterDetailsHtml(html, "Sample Char");

        assertThat(result).isPresent();
        CharacterDetails details = result.orElseThrow();
        assertThat(details.currentName()).isEqualTo("Sample Char");
        assertThat(details.formerNames()).containsExactly("Old Sample", "Older Sample");
        assertThat(details.sex()).isEqualTo(CharacterEntity.Sex.male);
        assertThat(details.vocation()).isEqualTo("Elite Knight");
        assertThat(details.level()).isEqualTo(123);
        assertThat(details.achievementPoints()).isEqualTo(456);
        assertThat(details.residence()).isEqualTo("Thais");
        assertThat(details.lastLogin()).isEqualTo(OffsetDateTime.of(2026, 5, 27, 21, 15, 33, 0, ZoneOffset.ofHours(2)));
        assertThat(details.accountStatus()).isEqualTo("Premium Account");
        assertThat(details.creationDate()).isEqualTo(Instant.parse("2020-05-01T09:00:00Z"));
        assertThat(details.world()).isEqualTo("Antica");
    }
}
''')

(tests / 'JsoupGuildAdapterCharacterizationTest.java').write_text('''package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.GuildScrapePort.GuildDetail;
import com.nathan.tibiastats.domain.port.GuildScrapePort.Member;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupGuildAdapterCharacterizationTest {

    @Test
    void parsesGuildDetailMembersAndInvitesFromFixtureWithoutNetworkAccess() throws Exception {
        String html = Files.readString(Path.of("src/test/resources/fixtures/tibia/guild-detail.html"));

        GuildDetail detail = new JsoupGuildAdapter().parseGuildDetailHtml(html, "Raw Raw");

        assertThat(detail.name()).isEqualTo("Raw Raw");
        assertThat(detail.worldName()).isEqualTo("Antica");
        assertThat(detail.description()).isEqualTo("Neutral guild");
        assertThat(detail.homepage()).isEqualTo("https://example.test");
        assertThat(detail.foundedAt()).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(detail.memberCount()).isEqualTo(2);
        assertThat(detail.onlineCount()).isEqualTo(1);
        assertThat(detail.rawHash()).isNotBlank();

        assertThat(detail.members()).extracting(Member::name)
                .contains("Guild Leader", "Guild Member");
        assertThat(detail.members())
                .anySatisfy(member -> {
                    assertThat(member.name()).isEqualTo("Guild Leader");
                    assertThat(member.title()).isEqualTo("The Boss");
                    assertThat(member.vocation()).isEqualTo("Elite Knight");
                    assertThat(member.level()).isEqualTo(500);
                    assertThat(member.joinedOn()).isEqualTo(LocalDate.of(2026, 5, 1));
                    assertThat(member.online()).isTrue();
                });
        assertThat(detail.invites()).extracting(invite -> invite.characterName())
                .contains("Invited Char");
    }
}
''')
PY

find src/main/java src/test/java -name '*.java' -type f -exec chmod 0644 {} +
chmod 0755 "$0" 2>/dev/null || true

cat <<MSG
Done. Character and guild scraper fixture characterization tests applied.
Backup directory: $BACKUP_DIR
Next step: make test
MSG
