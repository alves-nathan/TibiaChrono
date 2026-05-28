package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.GuildQueryService;
import com.nathan.tibiastats.application.service.GuildScrapeService;
import com.nathan.tibiastats.domain.model.GuildMembershipEventType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
public class GuildController {
    private final GuildQueryService guilds;
    private final GuildScrapeService scraper;

    public GuildController(GuildQueryService guilds, GuildScrapeService scraper) {
        this.guilds = guilds;
        this.scraper = scraper;
    }

    @GetMapping("/api/guilds")
    public List<GuildQueryService.GuildView> listGuilds(
            @RequestParam(required = false) String world,
            @RequestParam(required = false) Boolean active
    ) {
        return guilds.findGuilds(world, active);
    }

    @GetMapping("/api/guilds/{name}")
    public GuildQueryService.GuildView getGuild(@PathVariable String name) {
        try {
            return guilds.findGuild(name);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/api/guilds/{name}/members")
    public List<GuildQueryService.GuildMemberView> getGuildMembers(
            @PathVariable String name,
            @RequestParam(required = false, defaultValue = "true") Boolean active
    ) {
        try {
            return guilds.findMembers(name, active);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/api/guilds/{name}/events")
    public List<GuildQueryService.GuildMembershipEventView> getGuildEvents(
            @PathVariable String name,
            @RequestParam(required = false) GuildMembershipEventType type,
            @RequestParam(required = false) String characterName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return guilds.findEvents(name, characterName, type, from, to, limit);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @GetMapping("/api/characters/{name}/guild-history")
    public List<GuildQueryService.GuildMemberView> getCharacterGuildHistory(@PathVariable String name) {
        try {
            return guilds.findCharacterGuildHistory(name);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PostMapping("/api/scrape/guilds/worlds/{world}")
    public GuildScrapeService.GuildListResult scrapeGuildList(@PathVariable String world) {
        return scraper.updateGuildListForWorld(world);
    }

    @PostMapping("/api/scrape/guilds/{guildName}")
    public GuildScrapeService.GuildDetailResult scrapeGuildDetail(@PathVariable String guildName) {
        return scraper.updateGuildDetail(guildName);
    }
}
