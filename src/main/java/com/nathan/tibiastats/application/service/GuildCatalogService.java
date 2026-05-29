package com.nathan.tibiastats.application.service;

import com.nathan.tibiastats.domain.model.Guild;
import com.nathan.tibiastats.domain.model.World;
import com.nathan.tibiastats.domain.port.GuildCatalogRepositoryPort;
import com.nathan.tibiastats.domain.port.GuildScrapePort;
import com.nathan.tibiastats.domain.port.WorldRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class GuildCatalogService {
    private static final Logger log = LoggerFactory.getLogger(GuildCatalogService.class);
    private static final String LOG_PREFIX = "[GUILD_SCRAPER]";

    private final GuildScrapePort scraper;
    private final GuildCatalogRepositoryPort guilds;
    private final WorldRepositoryPort worlds;
    private final Clock clock;

    @Autowired
    public GuildCatalogService(GuildScrapePort scraper,
                               GuildCatalogRepositoryPort guilds,
                               WorldRepositoryPort worlds) {
        this(scraper, guilds, worlds, Clock.systemUTC());
    }

    GuildCatalogService(GuildScrapePort scraper,
                        GuildCatalogRepositoryPort guilds,
                        WorldRepositoryPort worlds,
                        Clock clock) {
        this.scraper = scraper;
        this.guilds = guilds;
        this.worlds = worlds;
        this.clock = clock;
    }

    @Transactional
    public GuildScrapeService.GuildListResult updateGuildListForWorld(String worldName) {
        Instant observedAt = clock.instant();
        World world = ensureWorld(worldName);
        int processed = 0;
        int created = 0;
        int updated = 0;

        for (GuildScrapePort.GuildListItem item : scraper.fetchGuildList(worldName)) {
            if (isBlank(item.name())) continue;
            processed++;
            GuildUpdate result = upsertGuild(item.name(), item.worldName(), item.description(), null, null, null, item.active(), observedAt);
            if (result.created()) created++; else updated++;
            Guild guild = result.guild();
            if (guild.getWorld() == null) guild.setWorld(world);
            guilds.saveGuild(guild);
        }

        log.info("{} Guild list updated: world={}, processed={}, created={}, updated={}", LOG_PREFIX, worldName, processed, created, updated);
        return new GuildScrapeService.GuildListResult(processed, created, updated);
    }

    GuildUpdate upsertGuild(String name,
                            String worldName,
                            String description,
                            String homepage,
                            String logoUrl,
                            LocalDate foundedAt,
                            boolean active,
                            Instant observedAt) {
        String normalizedName = normalizeGuildName(name);
        Guild guild = guilds.findGuild(name).orElseGet(Guild::new);
        boolean created = guild.getId() == null;

        guild.setName(normalizeDisplayName(name));
        guild.setNormalizedName(normalizedName);
        guild.setWorld(ensureWorld(firstNonBlank(worldName, guild.getWorld() == null ? null : guild.getWorld().getName())));
        if (!isBlank(description)) guild.setDescription(description.trim());
        if (!isBlank(homepage)) guild.setHomepage(homepage.trim());
        if (logoUrl != null) {
            guild.setLogoUrl(blankToNull(logoUrl));
        } else if (isKnownInvalidGuildLogo(guild.getLogoUrl())) {
            guild.setLogoUrl(null);
        }
        if (foundedAt != null) guild.setFoundedAt(foundedAt);
        guild.setActive(active);
        guild.setLastSeenAt(observedAt);

        return new GuildUpdate(guilds.saveGuild(guild), created);
    }

    World ensureWorld(String worldName) {
        String value = firstNonBlank(worldName, "Unknown");
        return worlds.findByName(value).orElseGet(() -> worlds.save(new World(value, null, null)));
    }

    private static boolean isKnownInvalidGuildLogo(String logoUrl) {
        if (isBlank(logoUrl)) return false;
        String lower = logoUrl.toLowerCase(Locale.ROOT);
        return lower.contains("headline-guilds.gif") || lower.contains("/strings/headline");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return "Unknown";
    }

    private static String normalizeDisplayName(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String normalizeGuildName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    record GuildUpdate(Guild guild, boolean created) {}
}
