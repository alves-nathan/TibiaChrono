package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.GuildScrapePort.GuildDetail;
import com.nathan.tibiastats.domain.port.GuildScrapePort.Invite;
import com.nathan.tibiastats.domain.port.GuildScrapePort.Member;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.blankToNull;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.extractCharacterName;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.extractGuildName;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.firstNonBlank;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.firstNonBlankOrNull;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.normalize;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.parseDate;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.removeWholeValue;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.sha256;

@Component
public class GuildDetailPageParser {
    private static final Pattern LEVEL_PATTERN = Pattern.compile("^\\d{1,5}$");
    private static final String FIELD_PATTERN_TEMPLATE = "%s\\s*([^:]+?)(?=\\s+(World|Guild Description|Homepage|Founded|Guildhall|Application|Members|Invited Characters|Navigation):|$)";
    private static final List<String> VOCATION_WORDS = List.of(
            "No Vocation", "Elder Druid", "Master Sorcerer", "Elite Knight", "Royal Paladin", "Exalted Monk",
            "None", "Druid", "Sorcerer", "Knight", "Paladin", "Monk"
    );

    public GuildDetail parseHtml(String html, String guildName) {
        Document doc = Jsoup.parse(html == null ? "" : html, TibiaGuildHttpClient.BASE_URL);
        return parse(doc, guildName);
    }

    public GuildDetail parse(Document doc, String guildName) {
        String pageText = doc.text();
        String requestedGuildName = normalize(guildName);
        String extractedGuildName = sanitizeExtractedGuildName(extractGuildNameFromDetailPage(doc));
        String name = firstNonBlank(requestedGuildName, extractedGuildName);
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
        Integer memberCount = parseMemberCount(pageText);
        Integer onlineCount = parseOnlineCount(pageText);
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
                    .map(GuildPageParsingSupport::normalize)
                    .filter(s -> !s.isBlank())
                    .toList();
            String vocation = findVocation(cells).orElse(null);
            Integer level = findLevel(cells).orElse(null);
            LocalDate joinedOn = findJoiningDate(cells).orElse(null);
            boolean online = cells.stream().anyMatch(GuildDetailPageParser::isOnlineStatus)
                    || rowText.toLowerCase(Locale.ROOT).endsWith(" online");
            String title = extractTitleFromNameCell(row, characterLink, name);
            String rowRank = extractRankFromMemberRow(row, name, vocation, level);
            if (rowRank != null && !rowRank.isBlank()) {
                currentRank = rowRank;
            }
            String rankName = firstNonBlankOrNull(currentRank, rowRank);

            members.add(new Member(name, blankToNull(rankName), blankToNull(title), blankToNull(vocation), level, joinedOn, online));
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

    private static String sanitizeExtractedGuildName(String value) {
        String normalized = normalize(value);
        return isValidExtractedGuildName(normalized) ? normalized : null;
    }

    private static boolean isValidExtractedGuildName(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return false;
        String lower = normalized.toLowerCase(Locale.ROOT);
        return !lower.equals("guilds")
                && !lower.equals("information")
                && !lower.equals("guild information")
                && !lower.equals("guild members")
                && !lower.equals("invited characters")
                && !lower.equals("navigation");
    }

    private static String extractGuildNameFromDetailPage(Document doc) {
        for (Element headline : doc.select("h1, .BoxContent .Text, .BoxContent")) {
            String text = normalize(headline.text());
            Matcher matcher = Pattern.compile("Guild(?: Information)?\\s+(.+?)(?:\\s+World:|$)", Pattern.CASE_INSENSITIVE).matcher(text);
            if (matcher.find()) {
                String candidate = normalize(matcher.group(1));
                if (isValidExtractedGuildName(candidate) && candidate.length() <= 80) return candidate;
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
        Pattern pattern = Pattern.compile(String.format(FIELD_PATTERN_TEMPLATE, Pattern.quote(label)), Pattern.CASE_INSENSITIVE);
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

    private static String extractRankFromMemberRow(Element row, String name, String vocation, Integer level) {
        String rank = normalize(row.text()).replace("[sort]", " ");
        if (rank.isBlank()) return null;

        String linkText = row.select("a[href*=subtopic=characters], a[href*=characters]").stream()
                .map(Element::text)
                .map(GuildPageParsingSupport::normalize)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse("");

        rank = removeWholeValue(rank, name);
        rank = removeWholeValue(rank, linkText);
        if (vocation != null) rank = removeWholeValue(rank, vocation);
        if (level != null) rank = removeWholeValue(rank, String.valueOf(level));

        for (Element td : row.select("td")) {
            String value = normalize(td.text());
            if (parseDate(value) != null || isOnlineStatus(value) || LEVEL_PATTERN.matcher(value).matches()) {
                rank = removeWholeValue(rank, value);
            }
        }

        rank = rank.replaceAll("(?i)\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2}\\s+\\d{4}\\b", " ");
        rank = rank.replaceAll("(?i)\\b(online|offline)\\b", " ");
        rank = rank.replaceAll("(?i)\\b(name and title|vocation|level|joining date|status)\\b", " ");
        rank = normalize(rank);

        if (rank.isBlank()) return null;
        if (parseDate(rank) != null || isOnlineStatus(rank) || LEVEL_PATTERN.matcher(rank).matches()) return null;
        String lower = rank.toLowerCase(Locale.ROOT);
        if (lower.contains("name and title") || lower.contains("joining date") || lower.contains("vocation")) return null;
        if (lower.equals(name.toLowerCase(Locale.ROOT))) return null;
        return rank;
    }

    private static String extractTitleFromNameCell(Element row, Element characterLink, String name) {
        Element cell = characterLink.parents().stream()
                .filter(e -> "td".equalsIgnoreCase(e.tagName()))
                .filter(e -> normalize(e.text()).toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseGet(() -> row.select("td").stream()
                        .filter(e -> normalize(e.text()).toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                        .findFirst()
                        .orElse(row.selectFirst("td")));
        if (cell == null) return null;

        String title = normalize(cell.text());
        String linkText = normalize(characterLink.text());
        if (!linkText.isBlank()) title = removeWholeValue(title, linkText);
        title = removeWholeValue(title, name);

        for (String vocation : VOCATION_WORDS) {
            title = removeWholeValue(title, vocation);
        }

        for (String part : title.split("\\s+")) {
            String normalized = normalize(part);
            if (LEVEL_PATTERN.matcher(normalized).matches()) {
                title = removeWholeValue(title, normalized);
            }
        }

        for (Element td : row.select("td")) {
            String value = normalize(td.text());
            LocalDate date = parseDate(value);
            if (date != null) title = removeWholeValue(title, value);
        }

        title = title.replaceAll("(?i)\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2}\\s+\\d{4}\\b", " ");
        title = title.replaceAll("(?i)\\b(online|offline)\\b", " ");
        title = normalize(title);
        if (title.isBlank()) return null;
        if (parseDate(title) != null || isOnlineStatus(title)) return null;
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("name and title") || lower.contains("vocation") || lower.contains("joining date")) return null;
        if (lower.equals(name.toLowerCase(Locale.ROOT))) return null;
        return title;
    }

    private static boolean isOnlineStatus(String value) {
        String lower = normalize(value).toLowerCase(Locale.ROOT);
        return lower.equals("online") || lower.equals("offline");
    }

    private static Integer parseMemberCount(String text) {
        Integer labeledCount = parseNumberAfterLabel(text, "Members:");
        if (labeledCount != null) return labeledCount;
        return parseNumberBefore(text, "members");
    }

    private static Integer parseOnlineCount(String text) {
        Integer labeledCount = parseNumberAfterLabel(text, "Online:");
        if (labeledCount != null) return labeledCount;
        return parseNumberBefore(text, "online");
    }

    private static Integer parseNumberAfterLabel(String text, String label) {
        if (text == null || label == null) return null;
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static Integer parseNumberBefore(String text, String word) {
        Matcher matcher = Pattern.compile("(\\d+)\\s+" + Pattern.quote(word), Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }
}
