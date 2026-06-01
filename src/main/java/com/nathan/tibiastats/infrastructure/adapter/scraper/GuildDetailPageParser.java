package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.port.GuildScrapePort.GuildDetail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.nathan.tibiastats.infrastructure.adapter.scraper.GuildPageParsingSupport.blankToNull;

@Component
public class GuildDetailPageParser {
    private final GuildDetailSummaryParser summaryParser;
    private final GuildMemberTableParser memberParser;
    private final GuildInviteTableParser inviteParser;

    public GuildDetailPageParser() {
        this(new GuildDetailSummaryParser(), new GuildMemberTableParser(), new GuildInviteTableParser());
    }

    @Autowired
    GuildDetailPageParser(
            GuildDetailSummaryParser summaryParser,
            GuildMemberTableParser memberParser,
            GuildInviteTableParser inviteParser
    ) {
        this.summaryParser = summaryParser;
        this.memberParser = memberParser;
        this.inviteParser = inviteParser;
    }

    public GuildDetail parseHtml(String html, String guildName) {
        Document doc = Jsoup.parse(html == null ? "" : html, TibiaGuildHttpClient.BASE_URL);
        return parse(doc, guildName);
    }

    public GuildDetail parse(Document doc, String guildName) {
        var summary = summaryParser.parse(doc, guildName);
        var members = memberParser.parse(doc);
        var memberCount = summary.memberCount();
        var onlineCount = summary.onlineCount();

        if (memberCount == null && !members.isEmpty()) {
            memberCount = members.size();
        }
        if (onlineCount == null && !members.isEmpty()) {
            onlineCount = (int) members.stream().filter(member -> member.online()).count();
        }

        return new GuildDetail(
                summary.name(),
                blankToNull(summary.world()),
                blankToNull(summary.description()),
                blankToNull(summary.homepage()),
                blankToNull(summary.logoUrl()),
                summary.foundedAt(),
                memberCount,
                onlineCount,
                summary.rawHash(),
                members,
                inviteParser.parse(doc)
        );
    }
}
