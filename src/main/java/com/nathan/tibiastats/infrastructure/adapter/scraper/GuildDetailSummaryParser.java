package com.nathan.tibiastats.infrastructure.adapter.scraper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.extractGuildName;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.firstNonBlank;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.firstNonBlankOrNull;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.normalize;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.parseDate;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.sha256;

@Component
public class GuildDetailSummaryParser {
    private static final String FIELD_PATTERN_TEMPLATE = "%s\\s*([^:]+?)(?=\\s+(World|Guild Description|Homepage|Founded|Guildhall|Application|Members|Invited Characters|Navigation):|$)";

    Summary parse(Document doc, String guildName) {
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

        return new Summary(
                name,
                world,
                description,
                homepage,
                findGuildLogoUrl(doc),
                foundedAt,
                parseMemberCount(pageText),
                parseOnlineCount(pageText),
                sha256(pageText)
        );
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

    record Summary(
            String name,
            String world,
            String description,
            String homepage,
            String logoUrl,
            LocalDate foundedAt,
            Integer memberCount,
            Integer onlineCount,
            String rawHash
    ) {}
}
