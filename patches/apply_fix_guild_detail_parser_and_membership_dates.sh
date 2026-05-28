#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

ADAPTER="src/main/java/com/nathan/tibiastats/infrastructure/adapter/scraper/JsoupGuildAdapter.java"
PORT="src/main/java/com/nathan/tibiastats/domain/port/GuildScrapePort.java"
SERVICE="src/main/java/com/nathan/tibiastats/application/service/GuildScrapeService.java"
MEMBERSHIP="src/main/java/com/nathan/tibiastats/domain/model/GuildMembership.java"
QUERY="src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java"
TEST="src/test/java/com/nathan/tibiastats/guild/GuildScrapeServiceIntegrationTest.java"
MIGRATION_DIR="src/main/resources/db/migration"

for file in "$ADAPTER" "$PORT" "$SERVICE" "$MEMBERSHIP" "$QUERY"; do
  if [[ ! -f "$file" ]]; then
    echo "ERROR: expected file not found: $file" >&2
    exit 1
  fi
done

cp "$ADAPTER" "$ADAPTER.bak-guild-parser-fix"
cp "$PORT" "$PORT.bak-guild-parser-fix"
cp "$SERVICE" "$SERVICE.bak-guild-parser-fix"
cp "$MEMBERSHIP" "$MEMBERSHIP.bak-guild-parser-fix"
cp "$QUERY" "$QUERY.bak-guild-parser-fix"
[[ -f "$TEST" ]] && cp "$TEST" "$TEST.bak-guild-parser-fix"

cat > "$ADAPTER" <<'JAVA'
package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.GuildScrapePort;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JsoupGuildAdapter implements GuildScrapePort {
    private static final String BASE_URL = "https://www.tibia.com/community/?subtopic=guilds";
    private static final int TIMEOUT_MS = 30_000;
    private static final Pattern CHARACTER_NAME_FROM_URL = Pattern.compile("[?&]name=([^&]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GUILD_NAME_FROM_URL = Pattern.compile("[?&]GuildName=([^&]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVEL_PATTERN = Pattern.compile("^\\d{1,5}$");
    private static final Pattern FIELD_PATTERN_TEMPLATE = Pattern.compile("%s\\s*([^:]+?)(?=\\s+(World|Guild Description|Homepage|Founded|Guildhall|Application|Members|Invited Characters|Navigation):|$)", Pattern.CASE_INSENSITIVE);
    private static final List<String> VOCATION_WORDS = List.of(
            "No Vocation", "Elder Druid", "Master Sorcerer", "Elite Knight", "Royal Paladin", "Exalted Monk",
            "None", "Druid", "Sorcerer", "Knight", "Paladin", "Monk"
    );

    @Override
    public List<GuildListItem> fetchGuildList(String worldName) {
        try {
            Document doc = Jsoup.connect(BASE_URL + "&world=" + encode(worldName))
                    .userAgent("TibiaChrono/1.0 (+https://github.com/nathan)")
                    .timeout(TIMEOUT_MS)
                    .get();

            Map<String, GuildListItem> items = new LinkedHashMap<>();
            for (Element link : doc.select("a[href*=GuildName]")) {
                String name = normalize(extractGuildName(link));
                if (name.isBlank()) continue;
                String rowText = link.parents().stream()
                        .filter(e -> "tr".equalsIgnoreCase(e.tagName()))
                        .findFirst()
                        .map(Element::text)
                        .orElse("");
                boolean active = !rowText.toLowerCase(Locale.ROOT).contains("disband");
                items.putIfAbsent(name.toLowerCase(Locale.ROOT), new GuildListItem(name, worldName, active, blankToNull(rowText)));
            }
            return List.copyOf(items.values());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch guild list for world '" + worldName + "'", e);
        }
    }

    @Override
    public GuildDetail fetchGuildDetail(String guildName) {
        try {
            Document doc = Jsoup.connect(BASE_URL + "&page=view&GuildName=" + encode(guildName))
                    .userAgent("TibiaChrono/1.0 (+https://github.com/nathan)")
                    .timeout(TIMEOUT_MS)
                    .get();

            String pageText = doc.text();
            String name = firstNonBlank(
                    extractGuildNameFromDetailPage(doc),
                    normalize(guildName)
            );
            String world = firstNonBlankOrNull(
                    valueFromTable(doc, "World"),
                    valueAfterLabel(pageText, "World:")
            );
            String homepage = firstNonBlankOrNull(
                    valueFromTable(doc, "Homepage"),
                    valueAfterLabel(pageText, "Homepage:")
            );
            String description = firstNonBlankOrNull(
                    valueFromTable(doc, "Guild Description"),
                    valueAfterLabel(pageText, "Guild Description:")
            );
            LocalDate foundedAt = parseDate(firstNonBlankOrNull(
                    valueFromTable(doc, "Founded"),
                    valueAfterLabel(pageText, "Founded:")
            ));
            Integer memberCount = parseNumberBefore(pageText, "members");
            Integer onlineCount = parseNumberBefore(pageText, "online");
            String logoUrl = findGuildLogoUrl(doc);

            List<Member> members = parseMembers(doc);
            if (memberCount == null && !members.isEmpty()) {
                memberCount = members.size();
            }
            if (onlineCount == null && !members.isEmpty()) {
                onlineCount = (int) members.stream().filter(Member::online).count();
            }

            return new GuildDetail(
                    name,
                    blankToNull(world),
                    blankToNull(description),
                    blankToNull(homepage),
                    blankToNull(logoUrl),
                    foundedAt,
                    memberCount,
                    onlineCount,
                    sha256(pageText),
                    members,
                    parseInvites(doc)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch guild detail for '" + guildName + "'", e);
        }
    }

    private List<Member> parseMembers(Document doc) {
        List<Member> members = new ArrayList<>();
        String currentRank = null;

        for (Element row : doc.select("tr")) {
            String rowText = normalize(row.text());
            if (rowText.isBlank()) continue;

            Element characterLink = firstCharacterLink(row);
            if (characterLink == null) {
                if (isMemberTableHeader(rowText) || isNonRankGuildSection(rowText)) {
                    continue;
                }
                if (looksLikeRankRow(row, rowText)) {
                    currentRank = cleanupRank(rowText);
                }
                continue;
            }

            String name = normalize(extractCharacterName(characterLink));
            if (name.isBlank()) continue;

            List<String> cells = row.select("td").stream()
                    .map(Element::text)
                    .map(JsoupGuildAdapter::normalize)
                    .filter(s -> !s.isBlank())
                    .toList();
            String vocation = findVocation(cells).orElse(null);
            Integer level = findLevel(cells).orElse(null);
            LocalDate joinedOn = findJoiningDate(cells).orElse(null);
            boolean online = cells.stream().anyMatch(JsoupGuildAdapter::isOnlineStatus)
                    || rowText.toLowerCase(Locale.ROOT).endsWith(" online");
            String title = extractTitleFromNameCell(row, characterLink, name);

            members.add(new Member(name, blankToNull(currentRank), blankToNull(title), blankToNull(vocation), level, joinedOn, online));
        }
        return members;
    }

    private List<Invite> parseInvites(Document doc) {
        List<Invite> invites = new ArrayList<>();
        for (Element row : doc.select("tr")) {
            String rowText = row.text().toLowerCase(Locale.ROOT);
            if (!rowText.contains("invite") && !rowText.contains("invited")) continue;
            Element characterLink = firstCharacterLink(row);
            if (characterLink == null) continue;
            invites.add(new Invite(normalize(extractCharacterName(characterLink)), findJoiningDate(List.of(row.text())).orElse(null)));
        }
        return invites;
    }

    private static Element firstCharacterLink(Element row) {
        return row.select("a[href*=subtopic=characters], a[href*=characters]").stream()
                .filter(link -> normalize(extractCharacterName(link)).length() > 1)
                .findFirst()
                .orElse(null);
    }

    private static String extractGuildNameFromDetailPage(Document doc) {
        for (Element headline : doc.select("h1, .BoxContent .Text, .BoxContent")) {
            String text = normalize(headline.text());
            Matcher matcher = Pattern.compile("Guild(?: Information)?\\s+(.+?)(?:\\s+World:|$)", Pattern.CASE_INSENSITIVE).matcher(text);
            if (matcher.find()) {
                String candidate = normalize(matcher.group(1));
                if (!candidate.equalsIgnoreCase("Guilds") && candidate.length() <= 80) return candidate;
            }
        }
        for (Element link : doc.select("a[href*=GuildName]")) {
            String value = normalize(extractGuildName(link));
            if (!value.isBlank()) return value;
        }
        return null;
    }

    private static String valueFromTable(Document doc, String label) {
        String expected = normalize(label).replace(":", "").toLowerCase(Locale.ROOT);
        for (Element row : doc.select("tr")) {
            Elements cells = row.select("td, th");
            if (cells.size() < 2) continue;
            String cellLabel = normalize(cells.get(0).text()).replace(":", "").toLowerCase(Locale.ROOT);
            if (cellLabel.equals(expected)) return normalize(cells.get(1).text());
        }
        return null;
    }

    private static String valueAfterLabel(String text, String label) {
        if (text == null || label == null) return null;
        Pattern pattern = Pattern.compile(String.format(FIELD_PATTERN_TEMPLATE.pattern(), Pattern.quote(label)), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? normalize(matcher.group(1)) : null;
    }

    private static String findGuildLogoUrl(Document doc) {
        for (Element img : doc.select("img")) {
            String src = img.absUrl("src");
            if (src == null || src.isBlank()) continue;
            String lower = src.toLowerCase(Locale.ROOT);
            if (lower.contains("headline") || lower.contains("/strings/") || lower.contains("button")) continue;
            if (lower.contains("guildlogo") || lower.contains("guildlogos") || lower.contains("/guilds/")) {
                return src;
            }
        }
        return null;
    }

    private static boolean isMemberTableHeader(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("name and title")
                && lower.contains("vocation")
                && lower.contains("level")
                && lower.contains("joining date")
                && lower.contains("status");
    }

    private static boolean isNonRankGuildSection(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.equals("guild members")
                || lower.equals("invited characters")
                || lower.contains("guild information")
                || lower.contains("guild description")
                || lower.contains("world:")
                || lower.contains("homepage:")
                || lower.contains("founded:")
                || lower.contains("application")
                || lower.contains("navigation")
                || lower.contains("disband")
                || lower.contains("currently no members");
    }

    private static boolean looksLikeRankRow(Element row, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (text.length() < 2 || text.length() > 80) return false;
        if (isMemberTableHeader(text) || isNonRankGuildSection(text)) return false;
        if (lower.contains("online") || lower.contains("offline")) return false;
        if (parseDate(text) != null) return false;
        Elements cells = row.select("td, th");
        if (cells.isEmpty()) return false;
        if (cells.size() == 1) return true;
        return cells.size() <= 2 && row.select("a").isEmpty();
    }

    private static String cleanupRank(String text) {
        return normalize(text)
                .replace("[sort]", "")
                .replace("Rank", "")
                .replace(":", "")
                .trim();
    }

    private static Optional<String> findVocation(List<String> cells) {
        for (String cell : cells) {
            for (String vocation : VOCATION_WORDS) {
                if (cell.equalsIgnoreCase(vocation)) return Optional.of(vocation);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> findLevel(List<String> cells) {
        for (String cell : cells) {
            String normalized = normalize(cell);
            if (parseDate(normalized) != null) continue;
            if (LEVEL_PATTERN.matcher(normalized).matches()) return Optional.of(Integer.parseInt(normalized));
        }
        return Optional.empty();
    }

    private static Optional<LocalDate> findJoiningDate(List<String> cells) {
        for (String cell : cells) {
            LocalDate date = parseDate(cell);
            if (date != null) return Optional.of(date);
        }
        return Optional.empty();
    }

    private static String extractTitleFromNameCell(Element row, Element characterLink, String name) {
        Element cell = characterLink.parents().stream()
                .filter(e -> "td".equalsIgnoreCase(e.tagName()))
                .findFirst()
                .orElse(row.selectFirst("td"));
        if (cell == null) return null;
        String text = normalize(cell.text());
        String linkText = normalize(characterLink.text());
        String title = text;
        if (!linkText.isBlank()) title = removeLeadingValue(title, linkText);
        title = removeLeadingValue(title, name);
        title = normalize(title);
        if (title.isBlank()) return null;
        if (parseDate(title) != null || isOnlineStatus(title)) return null;
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("name and title") || lower.contains("vocation") || lower.contains("joining date")) return null;
        return title;
    }

    private static String removeLeadingValue(String text, String value) {
        if (text == null || value == null) return text;
        String normalizedText = normalize(text);
        String normalizedValue = normalize(value);
        if (normalizedText.equalsIgnoreCase(normalizedValue)) return "";
        if (normalizedText.toLowerCase(Locale.ROOT).startsWith(normalizedValue.toLowerCase(Locale.ROOT) + " ")) {
            return normalizedText.substring(normalizedValue.length()).trim();
        }
        return normalizedText;
    }

    private static boolean isOnlineStatus(String value) {
        String lower = normalize(value).toLowerCase(Locale.ROOT);
        return lower.equals("online") || lower.equals("offline");
    }

    private static String extractGuildName(Element link) {
        String href = link.attr("href");
        Matcher matcher = GUILD_NAME_FROM_URL.matcher(href);
        if (matcher.find()) return decode(matcher.group(1));
        return link.text();
    }

    private static String extractCharacterName(Element link) {
        if (link == null) return "";
        String href = link.attr("href");
        Matcher matcher = CHARACTER_NAME_FROM_URL.matcher(href);
        if (matcher.find()) return decode(matcher.group(1));
        return link.text();
    }

    private static Integer parseNumberBefore(String text, String word) {
        Matcher matcher = Pattern.compile("(\\d+)\\s+" + Pattern.quote(word), Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH),
                DateTimeFormatter.ISO_LOCAL_DATE
        );
        String cleaned = normalize(text).replace(",", "");
        for (DateTimeFormatter formatter : formatters) {
            try { return LocalDate.parse(cleaned, formatter); } catch (Exception ignored) {}
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String firstNonBlankOrNull(String... values) {
        String value = firstNonBlank(values);
        return value.isBlank() ? null : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
JAVA

python3 - <<'PY'
from pathlib import Path
import re, sys

# GuildScrapePort: add joinedOn to Member record.
port = Path('src/main/java/com/nathan/tibiastats/domain/port/GuildScrapePort.java')
s = port.read_text()
old = '''    record Member(
            String name,
            String rankName,
            String title,
            String vocation,
            Integer level,
            boolean online
    ) {}'''
new = '''    record Member(
            String name,
            String rankName,
            String title,
            String vocation,
            Integer level,
            LocalDate joinedOn,
            boolean online
    ) {}'''
if old not in s:
    s2 = re.sub(r'''    record Member\(\s*String name,\s*String rankName,\s*String title,\s*String vocation,\s*Integer level,\s*boolean online\s*\) \{\}''', new, s, flags=re.S)
    if s2 == s:
        sys.exit('ERROR: could not patch GuildScrapePort.Member record')
    s = s2
else:
    s = s.replace(old, new)
port.write_text(s)

# GuildMembership: add joinedOn field.
membership = Path('src/main/java/com/nathan/tibiastats/domain/model/GuildMembership.java')
s = membership.read_text()
if 'java.time.LocalDate' not in s:
    s = s.replace('import java.time.Instant;\n', 'import java.time.Instant;\nimport java.time.LocalDate;\n')
if 'private LocalDate joinedOn;' not in s:
    s = s.replace('''    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
''', '''    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "joined_on")
    private LocalDate joinedOn;
''')
if 'getJoinedOn()' not in s:
    s = s.replace('''    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
''', '''    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public LocalDate getJoinedOn() { return joinedOn; }
    public void setJoinedOn(LocalDate joinedOn) { this.joinedOn = joinedOn; }
''')
membership.write_text(s)

# GuildScrapeService: set joined_on/joined_at from Tibia joining date and clear known invalid logo.
service = Path('src/main/java/com/nathan/tibiastats/application/service/GuildScrapeService.java')
s = service.read_text()
s = s.replace('''        if (!isBlank(logoUrl)) guild.setLogoUrl(logoUrl.trim());
''', '''        if (logoUrl != null) {
            guild.setLogoUrl(blankToNull(logoUrl));
        } else if (isKnownInvalidGuildLogo(guild.getLogoUrl())) {
            guild.setLogoUrl(null);
        }
''')
s = s.replace('''        membership.setLevel(member.level());
        membership.setJoinedAt(observedAt);
        membership.setFirstSeenAt(observedAt);
''', '''        membership.setLevel(member.level());
        membership.setJoinedOn(member.joinedOn());
        membership.setJoinedAt(toMembershipJoinedAt(member, observedAt));
        membership.setFirstSeenAt(observedAt);
''')
s = s.replace('''        membership.setLevel(member.level());
        membership.setLastSeenAt(observedAt);
''', '''        membership.setLevel(member.level());
        if (member.joinedOn() != null) {
            membership.setJoinedOn(member.joinedOn());
            membership.setJoinedAt(toMembershipJoinedAt(member, observedAt));
        }
        membership.setLastSeenAt(observedAt);
''')
if 'toMembershipJoinedAt(GuildScrapePort.Member member' not in s:
    insert = '''
    private static Instant toMembershipJoinedAt(GuildScrapePort.Member member, Instant observedAt) {
        if (member.joinedOn() == null) return observedAt;
        return member.joinedOn().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    }

    private static boolean isKnownInvalidGuildLogo(String logoUrl) {
        if (isBlank(logoUrl)) return false;
        String lower = logoUrl.toLowerCase(Locale.ROOT);
        return lower.contains("headline-guilds.gif") || lower.contains("/strings/headline");
    }
'''
    marker = '    private static <T> List<T> nullSafe(List<T> list) {'
    if marker not in s:
        sys.exit('ERROR: could not find insertion point in GuildScrapeService')
    s = s.replace(marker, insert + '\n' + marker)
service.write_text(s)

# GuildQueryService: expose joinedOn in member API.
query = Path('src/main/java/com/nathan/tibiastats/application/service/GuildQueryService.java')
s = query.read_text()
if 'java.time.LocalDate' not in s:
    s = s.replace('import java.time.Instant;\n', 'import java.time.Instant;\nimport java.time.LocalDate;\n')
if 'LocalDate joinedOn' not in s:
    s = s.replace('''                                  Instant joinedAt,
                                  Instant firstSeenAt,
''', '''                                  Instant joinedAt,
                                  LocalDate joinedOn,
                                  Instant firstSeenAt,
''')
    s = s.replace('''                    membership.getJoinedAt(),
                    membership.getFirstSeenAt(),
''', '''                    membership.getJoinedAt(),
                    membership.getJoinedOn(),
                    membership.getFirstSeenAt(),
''')
query.write_text(s)

# Tests: update fake member helper if present.
test = Path('src/test/java/com/nathan/tibiastats/guild/GuildScrapeServiceIntegrationTest.java')
if test.exists():
    s = test.read_text()
    s = s.replace('new GuildScrapePort.Member(name, rank, title, vocation, level, false)', 'new GuildScrapePort.Member(name, rank, title, vocation, level, null, false)')
    test.write_text(s)
PY

# Create next Flyway migration for official Tibia guild joining date.
mkdir -p "$MIGRATION_DIR"
if ! ls "$MIGRATION_DIR"/*__fix_guild_detail_parser_membership_dates.sql >/dev/null 2>&1; then
  max_version=$(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' \
    | sed -E 's#.*/V([0-9]+)__.*#\1#' \
    | sort -n \
    | tail -1)
  max_version=${max_version:-0}
  next_version=$((max_version + 1))
  cat > "$MIGRATION_DIR/V${next_version}__fix_guild_detail_parser_membership_dates.sql" <<'SQL'
-- Store the official Tibia guild joining date shown on the guild member table.
-- joined_at remains an instant used by the API/history model; when Tibia exposes
-- Joining Date, it is normalized to midnight UTC. first_seen_at still records
-- when this application first observed the membership.

ALTER TABLE guild_memberships
    ADD COLUMN IF NOT EXISTS joined_on DATE;

CREATE INDEX IF NOT EXISTS idx_guild_memberships_joined_on
    ON guild_memberships (guild_id, joined_on DESC, character_name_snapshot);
SQL
  echo "Created migration: $MIGRATION_DIR/V${next_version}__fix_guild_detail_parser_membership_dates.sql"
else
  echo "Migration for guild detail parser already exists; skipping creation."
fi

echo "Guild detail parser/member date fix applied."
echo "Next: ./run-tests.sh"
