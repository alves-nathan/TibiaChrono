package com.nathan.tibiastats.domain.port;

import java.util.List;
import java.util.Map;

public interface ScrapePort {
    record WorldSummary(String name, String pvptype, String location, int playersOnline) {}
    record WorldOnline(String world, int playersOnline, List<String> playerNames) {}

    List<WorldSummary> fetchWorldsOverview();
    WorldOnline fetchWorldPage(String worldName);

    boolean isFormerName(String oldName, String newName);
}
