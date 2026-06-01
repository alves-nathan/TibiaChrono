package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.GuildScrapePort.Member;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.blankToNull;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.extractCharacterName;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.findJoiningDate;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.findLevel;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.findVocation;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.firstCharacterLink;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.firstNonBlankOrNull;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.isLevel;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.isOnlineStatus;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.normalize;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.parseDate;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.removeWholeValue;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.vocationWords;

@Component
public class GuildMemberTableParser {
    List<Member> parse(Document doc) {
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
            boolean online = cells.stream().anyMatch(GuildPageParsingSupport::isOnlineStatus)
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
            if (parseDate(value) != null || isOnlineStatus(value) || isLevel(value)) {
                rank = removeWholeValue(rank, value);
            }
        }

        rank = rank.replaceAll("(?i)\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2}\\s+\\d{4}\\b", " ");
        rank = rank.replaceAll("(?i)\\b(online|offline)\\b", " ");
        rank = rank.replaceAll("(?i)\\b(name and title|vocation|level|joining date|status)\\b", " ");
        rank = normalize(rank);

        if (rank.isBlank()) return null;
        if (parseDate(rank) != null || isOnlineStatus(rank) || isLevel(rank)) return null;
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

        for (String vocation : vocationWords()) {
            title = removeWholeValue(title, vocation);
        }

        for (String part : title.split("\\s+")) {
            String normalized = normalize(part);
            if (isLevel(normalized)) {
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
}
