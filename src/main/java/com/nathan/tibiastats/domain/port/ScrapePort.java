package com.nathan.tibiastats.domain.port;

import com.nathan.tibiastats.domain.model.World;

import java.time.LocalDate;
import java.util.List;

public interface ScrapePort {
    record WorldSummary(String name, String pvptype, String location, int playersOnline) {}
    record WorldOnline(
            String world,
            int playersOnline,
            List<String> playerNames,
            String onlineRecord,
            LocalDate creationDate,
            String transferType,
            String gameWorldType
    ) {}

    List<WorldSummary> fetchWorldsOverview();
    WorldOnline fetchWorldPage(String worldName, World world);

    String getFormerName(String oldName);
}
