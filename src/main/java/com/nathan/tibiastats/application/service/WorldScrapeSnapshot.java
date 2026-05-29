package com.nathan.tibiastats.application.service;

import java.time.LocalDate;
import java.util.List;

public final class WorldScrapeSnapshot {
    private WorldScrapeSnapshot() {}

    public record Target(
            String name,
            String pvpType,
            String location,
            int playersOnline,
            String transferType,
            String gameWorldType
    ) {}

    public record OnlineCharacter(
            String name,
            Integer level,
            String vocation
    ) {}

    public record Page(
            Target target,
            String world,
            int playersOnline,
            List<OnlineCharacter> players,
            String onlineRecord,
            LocalDate creationDate,
            String transferType,
            String gameWorldType
    ) {}
}
