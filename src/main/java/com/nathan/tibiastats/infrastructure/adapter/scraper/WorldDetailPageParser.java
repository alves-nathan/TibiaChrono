package com.nathan.tibiastats.infrastructure.adapter.scraper;

import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.ScrapePort.OnlineCharacterSnapshot;
import com.nathan.tibiastats.domain.port.ScrapePort.WorldOnline;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.nathan.tibiastats.infrastructure.adapter.scraper.WorldPageParsingSupport.blankToNull;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.WorldPageParsingSupport.lastCellText;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.WorldPageParsingSupport.parseIntSafe;
import static com.nathan.tibiastats.infrastructure.adapter.scraper.WorldPageParsingSupport.parseIntegerOrNull;

@Component
public class WorldDetailPageParser {
    private static final DateTimeFormatter CREATION_DATE_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("MMMM uuuu")
            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
            .toFormatter(Locale.ENGLISH);

    public WorldOnline parse(Document doc, String worldName, World world) {
        int online = 0;
        List<OnlineCharacterSnapshot> players = new ArrayList<>();

        Elements rowsT1 = doc.select("table.Table1 div.InnerTableContainer tbody tr");
        for (Element tr : rowsT1) {
            String rowText = tr.text();
            String value = lastCellText(tr);

            if (rowText.contains("Players Online:")) {
                online = parseIntSafe(value);
                continue;
            }

            if (rowText.contains("Creation Date:") && world.getCreationDate() == null) {
                world.setCreationDate(LocalDate.parse(value, CREATION_DATE_FORMATTER));
                continue;
            }

            if (rowText.contains("Online Record:") && world.getOnlineRecord() == null) {
                world.setOnlineRecord(value);
                continue;
            }

            if (rowText.contains("PvP Type:") && world.getPvpType() == null) {
                world.setPvpType(value);
                continue;
            }

            if (rowText.contains("Transfer Type:")) {
                world.setTransferType(value);
                continue;
            }

            if (rowText.contains("Game World Type:")) {
                world.setGameWorldType(value);
            }
        }

        Elements rowsT2 = doc.select("table.Table2 div.InnerTableContainer tr, table.Table2 tr");
        for (Element tr : rowsT2) {
            String rowText = tr.text();
            if (rowText.contains("Name [sort] Level [sort] Vocation [sort]")) {
                continue;
            }

            Elements cols = tr.select("> td");
            if (cols.isEmpty()) {
                continue;
            }

            String name = tr.select("a[href*=name=]").text().trim();
            if (name.isBlank()) {
                continue;
            }

            Integer level = cols.size() > 1 ? parseIntegerOrNull(cols.get(1).text()) : null;
            String vocation = cols.size() > 2 ? blankToNull(cols.get(2).text()) : null;

            players.add(new OnlineCharacterSnapshot(name, level, vocation));
        }

        return new WorldOnline(
                worldName,
                online,
                players,
                world.getOnlineRecord(),
                world.getCreationDate(),
                world.getTransferType(),
                world.getGameWorldType()
        );
    }
}
